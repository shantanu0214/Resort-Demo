# Resort demo – run steps
Backend (Java 17 + Maven):  cd backend && mvn spring-boot:run     -> http://localhost:8080
Frontend (Node 18+):        cd frontend && npm install && npm run dev -> http://localhost:5173
Admin login: admin / admin123   (Basic auth, demo only)
Mobile app = PWA: phone var "Add to Home Screen" (HTTPS/LAN ip vaprun; API const App.jsx madhe LAN IP kara)

## Deploy
Backend  -> Render (Docker, root dir: backend), env: ADMIN_PASSWORD
Frontend -> Vercel/Netlify (root dir: frontend), env: VITE_API_URL=https://<render-url>/api

Render env for AI concierge: ANTHROPIC_API_KEY (set a spend limit in Anthropic console). Without it chat shows an 'off' message.
