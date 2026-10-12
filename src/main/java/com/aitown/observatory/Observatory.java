package com.aitown.observatory;

import com.aitown.sim.Archive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;

/** Local-only read-only event viewer. Does not expose agent library API. */
public final class Observatory {
    private static final String HTML = """
        <!doctype html><html lang="fr"><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>AI Town · Observatory</title>
        <style>
        :root{color-scheme:dark;font:15px system-ui,sans-serif;background:#0d1520;color:#e8eff5}
        body{margin:0 auto;max-width:1160px;padding:25px}
        header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap}
        h1{letter-spacing:.03em;margin:0 0 20px}
        form{display:flex;gap:10px;flex-wrap:wrap;margin:15px 0 24px}
        input,select,button{background:#192a3b;color:#eaf3fc;border:1px solid #405469;
          border-radius:7px;padding:12px;font:inherit}
        button{cursor:pointer;background:#345f87}input{min-width:180px}
        #status{color:#b0bcca}article{background:#142232;margin:8px 0;padding:13px 18px;
          border:1px solid #293e51;border-radius:8px}
        .meta{display:flex;gap:12px;flex-wrap:wrap;color:#aebfd1;font-size:13px}
        .type{color:#8bdabf;font-weight:bold}
        pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;color:#b9c9d6;margin-bottom:0}
        summary{cursor:pointer;padding:7px 0}
        </style>
        <header><h1>AI Town / Observatory</h1><span id="status">Chargement…</span></header>
        <p>Journal d'observation · Les événements sont privés et ne sont pas visibles des agents.</p>
        <form id="filters">
          <input name="q" placeholder="Recherche dans les événements" aria-label="Recherche">
          <select name="type" aria-label="Type"><option value="">Tous les types</option>
          <option>birth</option><option>death</option><option>decision</option>
          <option>action</option><option>writing</option><option>new_day</option></select>
          <input name="agent" placeholder="ID agent (facultatif)" inputmode="numeric" aria-label="Agent">
          <button type="submit">Rechercher</button>
        </form>
        <main id="results" aria-live="polite"></main>
        <script>
        const form=document.querySelector('#filters'),results=document.querySelector('#results');
        const status=document.querySelector('#status');
        async function load(){
          const values=new FormData(form),p=new URLSearchParams({limit:'150'});
          for(const k of ['q','type','agent'])if(String(values.get(k)).trim())p.set(k,values.get(k).trim());
          status.textContent='Recherche…';
          try{
            const response=await fetch('/api/events?'+p);
            if(!response.ok)throw new Error('HTTP '+response.status);
            const events=await response.json();
            results.replaceChildren();
            for(const e of events){
              const card=document.createElement('article');
              const meta=document.createElement('div');meta.className='meta';
              for(const [label,value] of [['Type',e.type],['Tick',e.tick],
                         ['Agent',e.agent_id??'—'],['Session',e.session.slice(0,8)]]){
                const span=document.createElement('span');
                span.textContent=label+': '+value;
                if(label==='Type')span.className='type';
                meta.append(span);
              }
              const details=document.createElement('details');
              const summary=document.createElement('summary');summary.textContent='Inspecter événement '+e.id;
              const code=document.createElement('pre');code.textContent=JSON.stringify(e.payload,null,2);
              details.append(summary,code);card.append(meta,details);results.append(card);
            }
            status.textContent=events.length+' événement(s) · résultats les plus récents';
          }catch(e){status.textContent='Erreur: '+e.message}
        }
        form.addEventListener('submit',e=>{e.preventDefault();load()});
        load();
        </script></html>
        """;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8765;
        Archive archive = new Archive(Path.of("data"));
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", exchange -> send(exchange, 200, "text/html; charset=utf-8", HTML));
        server.createContext("/api/events", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain", "Method not allowed"); return;
            }
            Map<String,String> params = parse(exchange.getRequestURI().getRawQuery());
            try {
                Integer agent = params.containsKey("agent") ? Integer.parseInt(params.get("agent")) : null;
                String json = archive.searchEvents(params.get("q"), params.get("type"), agent, 150);
                send(exchange, 200, "application/json; charset=utf-8", json);
            } catch (SQLException | NumberFormatException e) {
                send(exchange, 400, "text/plain; charset=utf-8", "Invalid search query");
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            try { archive.close(); } catch (IOException ignored) {}
        }));
        server.start();
        System.out.println("Observatory: http://localhost:" + port);
    }
    private static Map<String,String> parse(String query) {
        Map<String,String> values = new HashMap<>();
        if(query == null || query.isEmpty()) return values;
        for(String part : query.split("&")) {
            String[] kv = part.split("=",2);
            values.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),
                    URLDecoder.decode(kv.length>1?kv[1]:"",StandardCharsets.UTF_8));
        }
        return values;
    }
    private static void send(HttpExchange exchange,int code,String type,String body) throws IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",type);
        exchange.sendResponseHeaders(code,bytes.length);
        try(OutputStream out=exchange.getResponseBody()){out.write(bytes);}
    }
}
