package com.ashenwarden.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Browser front-end for the game: spawns the same program as a child process
 * with piped stdin/stdout and serves a Freebuff-style page that streams the
 * output, turns option lists into clickable chips and sends typed commands.
 *
 * <p>The console version is untouched — this is an extra way to play. Only the
 * JDK's built-in HTTP server is used, no dependencies, localhost only.</p>
 */
public final class WebInterface {

    /** One game session per server; output is kept for reconnecting clients. */
    private final Process game;
    private final HttpServer server;
    private final StringBuilder log = new StringBuilder();
    private final Object lock = new Object();

    private WebInterface(Process game, HttpServer server) {
        this.game = game;
        this.server = server;
    }

    /**
     * Starts the server on {@code port} and the game as a child process.
     * Returns once both are up; the caller should block on {@link #await()}.
     */
    public static WebInterface launch(int port, List<String> gameArgs) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Dstdout.encoding=UTF-8");
        command.add("-Dstderr.encoding=UTF-8");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.ashenwarden.Main");
        command.addAll(gameArgs);

        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Process child = builder.start();

        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        WebInterface web = new WebInterface(child, http);
        http.createContext("/", web::page);
        http.createContext("/stream", web::stream);
        http.createContext("/cmd", web::command);
        http.setExecutor(Executors.newFixedThreadPool(4));
        http.start();

        Thread pump = new Thread(web::pump, "game-output");
        pump.setDaemon(true);
        pump.start();
        return web;
    }

    /** Blocks until the game process ends, then stops the server. */
    public int await() throws InterruptedException {
        int code = game.waitFor();
        server.stop(1);
        return code;
    }

    /** Copies child output into the log; every append wakes the stream. */
    private void pump() {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        byte[] buffer = new byte[4096];
        try (InputStream in = game.getInputStream()) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                String chunk = decoder.decode(ByteBuffer.wrap(buffer, 0, read)).toString();
                synchronized (lock) {
                    log.append(chunk);
                    lock.notifyAll();
                }
            }
        } catch (IOException e) {
            // child closed its stream — the session simply ends
        }
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    // --- handlers ----------------------------------------------------------

    private void page(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "text/html; charset=utf-8", PAGE.getBytes(StandardCharsets.UTF_8));
    }

    /** Server-sent events: full history first, then live output as it happens. */
    private void stream(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        long sent = 0;
        try {
            while (true) {
                String data;
                synchronized (lock) {
                    if (sent >= log.length()) {
                        lock.wait(15_000);
                    }
                    if (sent >= log.length()) {
                        out.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        continue;
                    }
                    data = log.substring((int) sent);
                    sent = log.length();
                }
                for (String line : data.split("\n", -1)) {
                    out.write(("data: " + line + "\n").getBytes(StandardCharsets.UTF_8));
                }
                out.write("\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException | InterruptedException e) {
            // client went away or server stopped
        } finally {
            exchange.close();
        }
    }

    /** Accepts one command line ("cmd=look") and feeds the child process. */
    private void command(HttpExchange exchange) throws IOException {
        String body = readBody(exchange.getRequestBody());
        String cmd = body;
        int at = body.indexOf("cmd=");
        if (at >= 0) {
            cmd = urlDecode(body.substring(at + 4));
        }
        cmd = cmd.replace("\r", "").replace("\n", "");
        try {
            OutputStream stdin = game.getOutputStream();
            stdin.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            respond(exchange, 204, "text/plain; charset=utf-8", new byte[0]);
        } catch (IOException e) {
            respond(exchange, 500, "text/plain; charset=utf-8",
                    "game session is closed".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readBody(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }

    private static String urlDecode(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '+') {
                out.append(' ');
            } else if (c == '%' && i + 2 < value.length()) {
                try {
                    out.append((char) Integer.parseInt(value.substring(i + 1, i + 3), 16));
                    i += 2;
                } catch (NumberFormatException e) {
                    out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static void respond(HttpExchange exchange, int status, String type, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    // --- page --------------------------------------------------------------

    private static final String PAGE = """

            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>ASHEN WARDEN: THE LAST EMBER</title>
            <style>
              :root { --ember:#ff8f1f; --bg:#0e1013; --panel:#15181d; --line:#242a33;
                      --text:#d7dce3; --dim:#8b93a1; --user:#141b24; --user-line:#2b3a4d; }
              * { box-sizing:border-box; }
              body { margin:0; background:var(--bg); color:var(--text);
                     font:14px/1.55 "Cascadia Code", Consolas, "DejaVu Sans Mono", monospace; }
              header { position:sticky; top:0; z-index:3; background:var(--panel);
                       border-bottom:1px solid var(--line); }
              .head-in { max-width:860px; margin:0 auto; padding:10px 16px;
                         display:flex; align-items:baseline; gap:10px; }
              .logo { color:var(--ember); font-weight:700; letter-spacing:1.5px; font-size:15px;
                      white-space:nowrap; }
              .sub { color:var(--dim); font-size:12px; letter-spacing:.5px; }
              .wrap { max-width:860px; margin:0 auto; padding:0 16px; }
              #log { min-height:50vh; max-height:calc(100vh - 215px); overflow-y:auto;
                     padding:14px 0 8px; }
              .msg { display:flex; margin:0 0 12px; }
              .msg .body { max-width:100%; min-width:0; }
              .msg.agent .body { width:100%; border-left:2px solid var(--ember);
                                 background:#12151a; border-radius:0 8px 8px 0;
                                 padding:8px 12px; }
              .msg.player { justify-content:flex-end; }
              .msg.player .body { background:var(--user); border:1px solid var(--user-line);
                                  border-radius:10px; padding:6px 12px; color:#9fc4e8; }
              #log pre { margin:0; white-space:pre-wrap; word-break:break-word; font:inherit; }
              #statusbar { max-width:860px; margin:0 auto; padding:2px 16px 6px;
                           color:var(--dim); font-size:12px; letter-spacing:.5px;
                           white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
              footer { background:var(--panel); border-top:1px solid var(--line);
                       position:sticky; bottom:0; z-index:3; }
              .foot-in { max-width:860px; margin:0 auto; padding:8px 16px 14px; }
              #chips { display:flex; flex-wrap:wrap; gap:8px; margin-bottom:10px; }
              button.chip { background:#1c212a; color:var(--text); border:1px solid var(--line);
                            border-radius:999px; padding:5px 12px; cursor:pointer;
                            font:inherit; font-size:13px; }
              button.chip:hover { border-color:var(--ember); color:var(--ember); }
              button.chip.cursor { border-color:var(--ember); color:var(--ember);
                                   box-shadow:0 0 0 1px var(--ember); }
              #form { display:flex; gap:8px; }
              #cmd { flex:1; background:#0b0d10; color:var(--text); border:1px solid var(--line);
                     border-radius:8px; padding:9px 12px; font:inherit; outline:none; }
              #cmd:focus { border-color:var(--ember); }
              #send { background:var(--ember); color:#141414; border:0; border-radius:8px;
                      padding:0 18px; font:inherit; font-weight:700; cursor:pointer; }
              @media (max-width:620px) { .sub { display:none; } }
            </style>
            </head>
            <body>
            <header><div class="head-in">
              <span class="logo">&#9670; ASHEN WARDEN</span>
              <span class="sub">THE LAST EMBER &mdash; browser session</span>
            </div></header>
            <div class="wrap"><div id="log"></div></div>
            <div id="statusbar"></div>
            <footer><div class="foot-in">
              <div id="chips"></div>
              <form id="form" autocomplete="off">
                <input id="cmd" placeholder="type a command, e.g. look / inventory / attack"
                       aria-label="command">
                <button id="send" type="submit">Send</button>
              </form>
            </div></footer>
            <script>
            const log = document.getElementById('log');
            const chips = document.getElementById('chips');
            const statusbar = document.getElementById('statusbar');
            const input = document.getElementById('cmd');
            let full = '';      // raw stream, kept for chips + status parsing
            let messages = [];  // finished agent replies (+ player commands), oldest first
            let current = '';   // lines of the reply still streaming in
            let cursor = -1;

            // --- ANSI -> HTML ----------------------------------------------------------
            const BASE = {30:'#c85050',31:'#c85050',32:'#6fcf7f',33:'#e6b455',
                           34:'#5fa8e8',35:'#c586e0',36:'#4fc3c7',37:'#d7dce3',90:'#8b93a1'};
            function rgb(n){
              if (n < 16) return null;
              if (n >= 232) { const v = 8 + (n - 232) * 10; return `rgb(${v},${v},${v})`; }
              const c = n - 16, r = Math.floor(c / 36), g = Math.floor(c / 6) % 6, b = c % 6;
              const f = v => v === 0 ? 0 : 55 + v * 40;
              return `rgb(${f(r)},${f(g)},${f(b)})`;
            }
            function esc(s){ return s.replace(/&/g,'&amp;').replace(/</g,'&lt;'); }
            function render(text){
              let out = '', bold = false, dim = false, color = '';
              const re = /\\x1b\\[[0-9;]*m/g; let last = 0, m;
              while ((m = re.exec(text)) !== null) {
                out += paint(text.slice(last, m.index), bold, dim, color);
                last = re.lastIndex;
                for (const code of m[0].slice(2, -1).split(';').map(x => x === '' ? 0 : +x)) {
                  if (code === 0) { bold = false; dim = false; color = ''; }
                  else if (code === 1) bold = true;
                  else if (code === 2) dim = true;
                  else if (code === 22) { bold = false; dim = false; }
                  else if (BASE[code]) color = BASE[code];
                  else if (code === 38) { /* consumed below via lookahead */ }
                }
                const raw = m[0];
                const pick = raw.match(/^\\x1b\\[38;5;(\\d+)m$/);
                if (pick) color = rgb(+pick[1]) || color;
                if (raw === '\\x1b[0m') color = '';
              }
              out += paint(text.slice(last), bold, dim, color);
              return out;
            }
            function paint(chunk, bold, dim, color){
              if (!chunk) return '';
              let style = '';
              if (bold) style += 'font-weight:700;';
              if (dim) style += 'opacity:.65;';
              if (color) style += 'color:' + color + ';';
              return style ? `<span style="${style}">${esc(chunk)}</span>` : esc(chunk);
            }

            // --- message framing -------------------------------------------------------
            const TAG_RE = /.*\\u25c6 ASHEN WARDEN [-\\u2500]*\\s*$/;  // reply tag (maybe glued to \\u276f)
            const STATUS_RE = /^\\s*\\u2770.*\\u2771\\s*$/;             // \\u2770 ... \\u2771 status pill
            const PROMPT_RE = /^\\s*\\u276f\\s*$/;                     // bare \\u276f prompt

            function pushTurn(text){ if (text.trim() !== '') messages.push({player: false, text}); }

            function absorb(chunk){
              for (const line of chunk.split('\\n')) {
                // test against the ANSI-free line, keep the raw one for colors
                const clean = line.replace(/\\x1b\\[[0-9;]*m/g,'');
                if (TAG_RE.test(clean)) { pushTurn(current); current = ''; continue; }
                if (STATUS_RE.test(clean)) { statusbar.textContent = clean.trim(); continue; }
                if (PROMPT_RE.test(clean)) continue;
                current += line + '\\n';
              }
            }

            function draw(){
              let html = '';
              for (const m of messages) {
                html += m.player
                  ? '<div class="msg player"><div class="body">' + esc(m.text) + '</div></div>'
                  : '<div class="msg agent"><div class="body"><pre>' + render(m.text) + '</pre></div></div>';
              }
              html += '<div class="msg agent"><div class="body"><pre>' + render(current) + '</pre></div></div>';
              log.innerHTML = html;
              log.scrollTop = log.scrollHeight;
            }

            // --- chips from the last screen --------------------------------------------
            function refreshChips(){
              let tail = full.slice(-8000).replace(/\\x1b\\[[0-9;]*[A-Za-z]/g,'');
              // chips describe the current screen only: cut at the last reply tag
              const cut = Math.max(tail.lastIndexOf('\\u25c6 ASHEN WARDEN'),
                                   tail.lastIndexOf('* ASHEN WARDEN'));
              if (cut >= 0) tail = tail.slice(cut);
              const lines = tail.split('\\n');
              const found = [];
              const seen = new Set();
              const add = (text, label) => {
                text = text.trim(); if (!text || seen.has(text)) return;
                seen.add(text); found.push({text, label: label || text});
              };
              // packed key bar: many "[n] Action" pairs on one plain line -> actions
              for (let i = lines.length - 1; i >= 0 && found.length < 10; i--) {
                const row = lines[i];
                if (/[\\u256d\\u2570\\u250c\\u2514]/.test(row)) continue; // card border, not a key bar
                const pairs = [...row.matchAll(/\\[(\\d)\\]\\s+([^\\[\\]]+)/g)];
                if (pairs.length >= 3) {
                  for (const p of pairs) {
                    add(p[2].replace(/[\\u2500-\\u257f]+/g, ' ').trim().toLowerCase());
                  }
                  break;
                }
              }
              // option lists: one "[n] label" per line -> the number
              const opts = [];
              for (let i = lines.length - 1; i >= 0 && opts.length < 8; i--) {
                const m = lines[i].match(/^\\s*(?:[|\\u2502]\\s*)?\\[(\\d{1,2})\\]\\s+(.+?)\\s*(?:[|\\u2502])?\\s*$/);
                if (m && m[1] !== '0') opts.push(m);
                if (opts.length >= 8) break;
              }
              for (const m of opts.reverse()) add(m[1], '[' + m[1] + '] ' + m[2].replace(/\\s+/g,' '));
              // suggested-next footer
              const sugg = [...tail.matchAll(/Suggested:\\s*(.+)$/gm)].pop();
              if (sugg) for (const part of sugg[1].split(/\\s*[|\\u00b7]\\s*/)) add(part);
              chips.innerHTML = '';
              for (const c of found.slice(0, 9)) {
                const b = document.createElement('button');
                b.className = 'chip'; b.textContent = c.label;
                b.onclick = () => send(c.text);
                chips.appendChild(b);
              }
              if (cursor >= chips.children.length) cursor = chips.children.length - 1;
              paintCursor();
            }

            // --- keyboard cursor: arrows move between chips, Enter presses --------------
            function paintCursor(){
              const kids = chips.children;
              for (let i = 0; i < kids.length; i++) {
                kids[i].className = 'chip' + (i === cursor ? ' cursor' : '');
              }
            }
            document.addEventListener('keydown', e => {
              const kids = chips.children;
              const typing = document.activeElement === input && input.value !== '';
              if (!kids.length || typing) return;
              if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
                e.preventDefault(); cursor = (cursor + 1) % kids.length; paintCursor();
              } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
                e.preventDefault();
                cursor = (cursor - 1 + kids.length) % kids.length; paintCursor();
              } else if (e.key === 'Enter' && cursor >= 0) {
                e.preventDefault(); kids[cursor].click();
              }
            });

            // --- io ---------------------------------------------------------------------
            function send(text){
              if (!text) return;
              fetch('/cmd', {method:'POST', body:'cmd=' + encodeURIComponent(text)});
              messages.push({player: true, text});
              input.value = '';
              draw();
            }
            document.getElementById('form').onsubmit = e => { e.preventDefault(); send(input.value.trim()); };
            new EventSource('/stream').onmessage = e => {
              const line = e.data;
              full += (full.endsWith('\\n') || full === '' ? '' : '\\n') + line + '\\n';
              absorb(line);
              draw();
              refreshChips();
            };
            input.focus();
            </script>
            </body>
            </html>

            """;

}
