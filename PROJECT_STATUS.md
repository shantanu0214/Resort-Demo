# Resort Management System – Project Status
## Done
- Spring Boot backend (backend/): House, availability, booking (mock payment), my bookings, admin stats/cancel/add room, Basic auth, H2 + seed data
- React + Vite web (frontend/): Book, My Bookings, Admin; PWA manifest + icons + service worker
- Deploy config: backend/Dockerfile (Render), VITE_API_URL (Vercel/Netlify)
- Concurrency fix: transactional BookingService with pessimistic row lock (no double booking)
- Dynamic pricing: weekend +20%, Oct-Feb +15%, last-minute +10% (GET /api/quote)
- AI concierge: floating chat -> POST /api/concierge -> Claude API (key server-side, env ANTHROPIC_API_KEY, optional CLAUDE_MODEL, rate limited 15/min/IP). No API key = rule-based DEMO MODE (not AI, labelled in chat UI)
- HLD page: resort-hld.html (architecture, ER, API list, demo script)
## Not verified yet
- mvn build / npm install were NOT run in the authoring environment. Run both first, fix any errors.
## Pending
- Deploy backend (Render, env ADMIN_PASSWORD) then frontend (Vercel, env VITE_API_URL=https://<render>/api)
- Wake Render 10 min before demo (free tier sleeps). H2 resets on restart.
## Future phase (HLD only)
- JWT auth, Razorpay, Redis cache, email/SMS, S3 images, MySQL/Postgres, native app
## Resume prompt for new chat
"Resort demo: upload resort-demo.zip; continue from PROJECT_STATUS.md"
