"""Minimal Gemini proxy. Deploy behind HTTPS reverse proxy and authentication.
Environment: GEMINI_API_KEY, APP_ACCESS_TOKEN. Python 3.10+, standard library only.
For personal testing only; add rate limiting before public deployment.
"""
import json, os, urllib.request, urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

KEYS=[os.environ.get("GEMINI_API_KEY_"+str(i),"") for i in range(1,6)]
KEYS=[k for k in KEYS if k]
if not KEYS and os.environ.get("GEMINI_API_KEY"): KEYS=[os.environ["GEMINI_API_KEY"]]
TOKEN=os.environ.get("APP_ACCESS_TOKEN","")
MODEL=os.environ.get("GEMINI_MODEL","gemini-2.5-flash")

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/generate":
            self.send_error(404); return
        if not KEYS:
            self.send_error(503,"Server key missing"); return
        if TOKEN and self.headers.get("Authorization") != "Bearer "+TOKEN:
            self.send_error(401); return
        try:
            size=int(self.headers.get("Content-Length","0"))
            if not 0<size<=2048: self.send_error(413); return
            payload_in=json.loads(self.rfile.read(size))
            topic=str(payload_in.get("topic",""))[:400]
            profile=int(payload_in.get("profile",1))
            if not 1 <= profile <= len(KEYS): self.send_error(400,"Unknown profile"); return
            key=KEYS[profile-1]
            prompt=("Viết lời đánh thức tiếng Việt độc đáo, đáng yêu, vui nhộn, dài 1-3 câu, "
                    "không markdown, không thông tin bịa về người dùng. Chủ đề: "+topic)
            body=json.dumps({"contents":[{"parts":[{"text":prompt}]}]}).encode()
            url="https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent"
            req=urllib.request.Request(url,data=body,headers={
                "x-goog-api-key":key,"Content-Type":"application/json"},method="POST")
            with urllib.request.urlopen(req,timeout=25) as resp:
                result=json.load(resp)
            message=result["candidates"][0]["content"]["parts"][0]["text"][:500]
            payload=json.dumps({"message":message},ensure_ascii=False).encode()
            self.send_response(200); self.send_header("Content-Type","application/json; charset=utf-8")
            self.send_header("Content-Length",str(len(payload))); self.end_headers();self.wfile.write(payload)
        except urllib.error.HTTPError as e:
            self.send_error(e.code,"Gemini API error (check key, billing, quota)")
        except Exception:
            self.send_error(502,"Gemini request failed")

if __name__=="__main__":
    ThreadingHTTPServer(("127.0.0.1",8765),Handler).serve_forever()
