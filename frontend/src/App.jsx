import { useState, useEffect } from "react";

const API = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
const J = { "Content-Type": "application/json" };
const MSG = { 409: "This room is no longer available for those dates.", 400: "Please check your details and dates.", 401: "Invalid credentials." };
const call = async (url, opt) => {
  const r = await fetch(API + url, opt);
  if (!r.ok) throw new Error(MSG[r.status] || "Something went wrong (" + r.status + ")");
  return r.json();
};
const inr = n => "₹" + Math.round(n).toLocaleString("en-IN");
const today = () => new Date(Date.now() - new Date().getTimezoneOffset() * 6e4).toISOString().slice(0, 10);
const go = id => setTimeout(() => document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" }), 60);

export default function App() {
  const [tab, setTab] = useState("stay");
  const tabs = [["stay", "🏨", "Stay"], ["mine", "🧾", "My Bookings"], ["admin", "📊", "Admin"]];
  return (
    <>
      <header className="top">
        <div className="logo">Aurelia<span>RESORT</span></div>
        <nav>{tabs.map(([k, i, v]) => (
          <button key={k} className={tab === k ? "on" : ""} onClick={() => setTab(k)}><i>{i}</i>{v}</button>))}
        </nav>
      </header>
      {tab === "stay" && <Stay />}
      {tab === "mine" && <Mine />}
      {tab === "admin" && <Admin />}
      <Chat />
    </>
  );
}

function Stay() {
  const [d, setD] = useState({ checkIn: "", checkOut: "" });
  const [all, setAll] = useState([]);
  const [rooms, setRooms] = useState(null);
  const [sel, setSel] = useState(null);
  const [q, setQ] = useState(null);
  const [f, setF] = useState({ guestName: "", email: "", phone: "" });
  const [done, setDone] = useState(null);
  const [err, setErr] = useState("");
  useEffect(() => { call("/rooms").then(setAll).catch(() => setErr("Cannot reach the server. Is the backend running?")); }, []);
  const nights = d.checkIn && d.checkOut ? Math.round((new Date(d.checkOut) - new Date(d.checkIn)) / 864e5) : 0;

  const search = async () => {
    setErr(""); setSel(null);
    try { setRooms(await call(`/rooms/available?checkIn=${d.checkIn}&checkOut=${d.checkOut}`)); go("rooms"); }
    catch (e) { setErr(e.message); }
  };
  const pick = async r => {
    setSel(r); setQ(null); setErr(""); go("summary");
    try { setQ(await call(`/quote?roomId=${r.id}&checkIn=${d.checkIn}&checkOut=${d.checkOut}`)); } catch {}
  };
  const pay = async () => {
    setErr("");
    try { setDone(await call("/bookings", { method: "POST", headers: J, body: JSON.stringify({ ...f, ...d, roomId: sel.id }) })); setSel(null); setRooms(null); window.scrollTo(0, 0); }
    catch (e) { setErr(e.message); }
  };

  if (done) return (
    <main className="wrap"><div className="success">
      <div className="tick">✓</div><h2>Booking confirmed</h2>
      <p>Reference <b>#{done.id}</b></p><p>{done.checkIn} → {done.checkOut}</p>
      <p className="big">{inr(done.total)}</p><small>Demo payment, no money was charged</small>
      <button onClick={() => setDone(null)}>Book another stay</button>
    </div></main>
  );

  const list = rooms || all;
  const adj = q && sel ? q.total - sel.price * nights : 0;
  return (
    <>
      <section className="hero"><div className="heroin">
        <p className="eyebrow">AURELIA RESORT</p>
        <h1>Where the forest meets luxury</h1>
        <p className="sub">Handcrafted stays, lake views and unhurried mornings.</p>
        <div className="search">
          <label>Check-in<input type="date" min={today()} value={d.checkIn} onChange={e => setD({ ...d, checkIn: e.target.value })} /></label>
          <label>Check-out<input type="date" min={d.checkIn || today()} value={d.checkOut} onChange={e => setD({ ...d, checkOut: e.target.value })} /></label>
          <button onClick={search} disabled={nights < 1}>Check availability</button>
        </div>
        {err && !sel && <p className="err">{err}</p>}
      </div></section>

      <main className="wrap" id="rooms">
        <h2>{rooms ? (rooms.length ? `${rooms.length} rooms available · ${nights} night${nights > 1 ? "s" : ""}` : "No rooms available for these dates") : "Our rooms"}</h2>
        <div className="grid">
          {list.map(r => (
            <article className="room" key={r.id}>
              <div className="img" style={{ backgroundImage: `url(${r.image})` }}><span className="badge">{r.type}</span></div>
              <div className="body">
                <h3>{r.name}</h3>
                <p className="meta">👥 Up to {r.capacity} guests · 🌿 Breakfast included</p>
                <div className="row"><div><small>from</small><b className="price">{inr(r.price)}</b><small> / night</small></div>
                  {rooms ? <button onClick={() => pick(r)}>Select</button> : <button className="ghost" onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}>Pick dates</button>}
                </div>
              </div>
            </article>))}
        </div>

        {sel && (
          <div className="summary" id="summary">
            <div>
              <h3>{sel.name}</h3>
              <p className="meta">{d.checkIn} → {d.checkOut} · {nights} night{nights > 1 ? "s" : ""}</p>
              <ul>
                <li><span>Base rate × {nights}</span><b>{inr(sel.price * nights)}</b></li>
                {q && <li><span>Weekend / season / last-minute</span><b>{adj >= 0 ? "+" : "−"}{inr(Math.abs(adj))}</b></li>}
                <li className="tot"><span>Total</span><b>{q ? inr(q.total) : "…"}</b></li>
              </ul>
            </div>
            <div className="fields">
              <input placeholder="Full name" value={f.guestName} onChange={e => setF({ ...f, guestName: e.target.value })} />
              <input placeholder="Email" value={f.email} onChange={e => setF({ ...f, email: e.target.value })} />
              <input placeholder="Phone" value={f.phone} onChange={e => setF({ ...f, phone: e.target.value })} />
              <button onClick={pay} disabled={!f.guestName || !f.email}>Pay & Confirm (demo)</button>
              {err && <p className="err dark">{err}</p>}
            </div>
          </div>)}
      </main>
    </>
  );
}

const Chip = ({ s }) => <span className={"chip " + s}>{s}</span>;

function Table({ rows, onCancel }) {
  return (
    <div className="scroll"><table>
      <thead><tr><th>#</th><th>Guest</th><th>Room</th><th>Dates</th><th>Total</th><th>Status</th>{onCancel && <th />}</tr></thead>
      <tbody>{rows.map(b => (
        <tr key={b.id}>
          <td>{b.id}</td><td>{b.guestName}<br /><small>{b.email}</small></td><td>{b.roomId}</td>
          <td>{b.checkIn} → {b.checkOut}</td><td>{inr(b.total)}</td><td><Chip s={b.status} /></td>
          {onCancel && <td>{b.status !== "CANCELLED" && <button className="sm" onClick={() => onCancel(b.id)}>Cancel</button>}</td>}
        </tr>))}
      </tbody>
    </table></div>
  );
}

function Mine() {
  const [email, setEmail] = useState("");
  const [rows, setRows] = useState(null);
  return (
    <main className="wrap page">
      <h2>My bookings</h2>
      <div className="inline">
        <input placeholder="Email used for booking" value={email} onChange={e => setEmail(e.target.value)} />
        <button onClick={async () => setRows(await call("/bookings?email=" + encodeURIComponent(email)))}>Show</button>
      </div>
      {rows && (rows.length ? <Table rows={rows} /> : <p className="meta">No bookings found for this email.</p>)}
    </main>
  );
}

function Admin() {
  const [cred, setCred] = useState({ u: "admin", p: "" });
  const [auth, setAuth] = useState(null);
  const [stats, setStats] = useState(null);
  const [rows, setRows] = useState([]);
  const [err, setErr] = useState("");
  const load = async h => {
    try {
      const o = { headers: { Authorization: h } };
      setStats(await call("/admin/stats", o)); setRows(await call("/admin/bookings", o)); setAuth(h); setErr("");
    } catch { setErr("Login failed"); }
  };
  const cancel = async id => { await call(`/admin/bookings/${id}/cancel`, { method: "PUT", headers: { Authorization: auth } }); load(auth); };

  if (!auth) return (
    <main className="wrap page"><div className="login">
      <h2>Admin login</h2>
      <input value={cred.u} onChange={e => setCred({ ...cred, u: e.target.value })} />
      <input type="password" placeholder="Password" value={cred.p} onChange={e => setCred({ ...cred, p: e.target.value })} />
      <button onClick={() => load("Basic " + btoa(cred.u + ":" + cred.p))}>Login</button>
      {err && <p className="err dark">{err}</p>}
    </div></main>
  );
  return (
    <main className="wrap page">
      <h2>Dashboard</h2>
      <div className="stats">
        <div><small>Rooms</small><b>{stats.rooms}</b></div>
        <div><small>Bookings</small><b>{stats.bookings}</b></div>
        <div><small>Revenue</small><b>{inr(stats.revenue)}</b></div>
        <div><small>Occupied today</small><b>{stats.occupiedToday}</b></div>
      </div>
      <Table rows={[...rows].sort((a, b) => b.id - a.id)} onCancel={cancel} />
    </main>
  );
}

function Chat() {
  const [open, setOpen] = useState(false);
  const [msgs, setMsgs] = useState([{ role: "assistant", content: "Hello! I'm the Aurelia concierge. Ask me about rooms, prices or planning your stay 🌴" }]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [demo, setDemo] = useState(false);
  const send = async () => {
    if (!text.trim() || busy) return;
    const next = [...msgs, { role: "user", content: text.trim() }];
    setMsgs(next); setText(""); setBusy(true);
    try {
      const r = await call("/concierge", { method: "POST", headers: J, body: JSON.stringify({ messages: next.slice(1) }) });
      if (r.mode) setDemo(r.mode === "demo");
      setMsgs([...next, { role: "assistant", content: r.reply }]);
    } catch { setMsgs([...next, { role: "assistant", content: "Sorry, something went wrong. Please try again shortly." }]); }
    setBusy(false);
  };
  return (
    <>
      <button className="fab" onClick={() => setOpen(!open)}>{open ? "✕" : "💬"}</button>
      {open && (
        <div className="chat">
          <div className="chead">Aurelia Concierge</div>
          {demo && <small className="note">Demo mode: simple rule-based replies, not full AI</small>}
          <div className="log">{msgs.map((m, i) => <p key={i} className={m.role}>{m.content}</p>)}{busy && <p className="assistant">…</p>}</div>
          <div className="in">
            <input value={text} placeholder="Ask a question…" onChange={e => setText(e.target.value)} onKeyDown={e => e.key === "Enter" && send()} />
            <button onClick={send} disabled={busy}>Send</button>
          </div>
        </div>)}
    </>
  );
}
