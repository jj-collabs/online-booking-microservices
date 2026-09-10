// The frontend talks ONLY to the API Gateway — never directly to a domain service.
// Change this if your gateway's host port differs from the docker-compose default.
const API_BASE = "http://localhost:8090/api";

const state = {
  token: sessionStorage.getItem("token") || null,
  userId: sessionStorage.getItem("userId") || null,
  email: sessionStorage.getItem("email") || null,
};

// ---------- helpers ----------

function authHeaders() {
  return state.token ? { Authorization: "Bearer " + state.token } : {};
}

async function api(method, path, body) {
  const res = await fetch(API_BASE + path, {
    method,
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const text = await res.text();
      if (text) message = text;
    } catch (_) {}
    throw new Error(message || `Request failed (${res.status})`);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

function toast(message, kind = "ok") {
  const el = document.getElementById("toast");
  el.textContent = message;
  el.className = "toast " + kind;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => el.classList.add("hidden"), 3500);
}

function fmtTime(iso) {
  const d = new Date(iso);
  return d.toLocaleString(undefined, {
    month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
  });
}

// ---------- clock ----------

function tickClock() {
  document.getElementById("clock").textContent = new Date().toLocaleTimeString();
}
setInterval(tickClock, 1000);
tickClock();

// ---------- auth ----------

const authSection = document.getElementById("authSection");
const appSection = document.getElementById("appSection");
const userBadge = document.getElementById("userBadge");
const userEmailEl = document.getElementById("userEmail");

function setSignedIn(auth) {
  state.token = auth.token;
  state.userId = String(auth.userId);
  state.email = auth.email;
  sessionStorage.setItem("token", state.token);
  sessionStorage.setItem("userId", state.userId);
  sessionStorage.setItem("email", state.email);
  renderAuthState();
  loadResources();
  loadMyBookings();
}

function signOut() {
  state.token = null;
  state.userId = null;
  state.email = null;
  sessionStorage.clear();
  renderAuthState();
}

function renderAuthState() {
  const signedIn = !!state.token;
  authSection.classList.toggle("hidden", signedIn);
  appSection.classList.toggle("hidden", !signedIn);
  userBadge.classList.toggle("hidden", !signedIn);
  if (signedIn) userEmailEl.textContent = state.email;
}

document.querySelectorAll(".tab-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".tab-btn").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    const tab = btn.dataset.tab;
    document.getElementById("loginForm").classList.toggle("hidden", tab !== "login");
    document.getElementById("registerForm").classList.toggle("hidden", tab !== "register");
  });
});

document.getElementById("loginForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const msg = document.getElementById("loginMsg");
  msg.textContent = "";
  const form = new FormData(e.target);
  try {
    const auth = await api("POST", "/users/auth/login", {
      email: form.get("email"),
      password: form.get("password"),
    });
    setSignedIn(auth);
    toast("Signed in as " + auth.email, "ok");
  } catch (err) {
    msg.textContent = err.message;
    msg.className = "form-msg error";
  }
});

document.getElementById("registerForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const msg = document.getElementById("registerMsg");
  msg.textContent = "";
  const form = new FormData(e.target);
  try {
    await api("POST", "/users/auth/register", {
      fullName: form.get("fullName"),
      email: form.get("email"),
      password: form.get("password"),
    });
    msg.textContent = "Account created — sign in above.";
    msg.className = "form-msg ok";
    document.querySelector('.tab-btn[data-tab="login"]').click();
    e.target.reset();
  } catch (err) {
    msg.textContent = err.message;
    msg.className = "form-msg error";
  }
});

document.getElementById("logoutBtn").addEventListener("click", signOut);

// ---------- resources ----------

const resourceRows = document.getElementById("resourceRows");

async function loadResources() {
  resourceRows.innerHTML = '<p class="empty">Loading resources…</p>';
  try {
    const resources = await api("GET", "/resources");
    renderResources(resources);
  } catch (err) {
    resourceRows.innerHTML = `<p class="empty">Could not load resources — ${err.message}</p>`;
  }
}

function renderResources(resources) {
  if (!resources.length) {
    resourceRows.innerHTML = '<p class="empty">No resources yet. Add one above.</p>';
    return;
  }
  resourceRows.innerHTML = "";
  resources.forEach((r) => {
    const row = document.createElement("div");
    row.className = "row";
    row.innerHTML = `
      <span class="row-name">${r.name}</span>
      <span class="row-meta">${r.type}</span>
      <span class="row-meta">${r.location}</span>
      <span class="row-meta">Cap ${r.capacity}</span>
      <span class="status-pill ${r.available ? "status-available" : "status-unavailable"}">
        ${r.available ? "available" : "unavailable"}
      </span>
      <button class="btn-ghost btn-sm book-toggle">Book</button>
      <form class="book-row-form">
        <input type="datetime-local" name="startTime" required />
        <input type="datetime-local" name="endTime" required />
        <button type="submit" class="btn-primary btn-sm">Confirm</button>
      </form>
    `;
    const toggleBtn = row.querySelector(".book-toggle");
    const bookForm = row.querySelector(".book-row-form");
    toggleBtn.addEventListener("click", () => bookForm.classList.toggle("open"));

    bookForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const fd = new FormData(bookForm);
      try {
        await api("POST", "/bookings", {
          userId: Number(state.userId),
          resourceId: r.id,
          startTime: fd.get("startTime"),
          endTime: fd.get("endTime"),
        });
        toast(`Booked "${r.name}"`, "ok");
        bookForm.classList.remove("open");
        loadMyBookings();
      } catch (err) {
        toast(err.message, "error");
      }
    });

    resourceRows.appendChild(row);
  });
}

const newResourceBtn = document.getElementById("newResourceBtn");
const resourceForm = document.getElementById("resourceForm");
newResourceBtn.addEventListener("click", () => resourceForm.classList.toggle("hidden"));

resourceForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(resourceForm);
  try {
    await api("POST", "/resources", {
      name: fd.get("name"),
      type: fd.get("type"),
      location: fd.get("location"),
      capacity: Number(fd.get("capacity")),
    });
    toast("Resource added", "ok");
    resourceForm.reset();
    resourceForm.classList.add("hidden");
    loadResources();
  } catch (err) {
    toast(err.message, "error");
  }
});

// ---------- bookings ----------

const bookingRows = document.getElementById("bookingRows");

async function loadMyBookings() {
  bookingRows.innerHTML = '<p class="empty">Loading your bookings…</p>';
  try {
    const bookings = await api("GET", `/bookings/user/${state.userId}`);
    renderBookings(bookings);
  } catch (err) {
    bookingRows.innerHTML = `<p class="empty">Could not load bookings — ${err.message}</p>`;
  }
}

function renderBookings(bookings) {
  if (!bookings.length) {
    bookingRows.innerHTML = '<p class="empty">No bookings yet — book a resource above.</p>';
    return;
  }
  bookingRows.innerHTML = "";
  bookings.forEach((b) => {
    const row = document.createElement("div");
    row.className = "row";
    const statusClass = b.status === "CONFIRMED" ? "status-confirmed" : "status-cancelled";
    row.innerHTML = `
      <span class="row-name">Resource #${b.resourceId}</span>
      <span class="row-meta">${fmtTime(b.startTime)}</span>
      <span class="row-meta">→ ${fmtTime(b.endTime)}</span>
      <span></span>
      <span class="status-pill ${statusClass}">${b.status.toLowerCase()}</span>
      <button class="btn-ghost btn-sm cancel-btn" ${b.status !== "CONFIRMED" ? "disabled" : ""}>Cancel</button>
    `;
    row.querySelector(".cancel-btn").addEventListener("click", async () => {
      try {
        await api("PUT", `/bookings/${b.id}/cancel`);
        toast("Booking cancelled", "ok");
        loadMyBookings();
      } catch (err) {
        toast(err.message, "error");
      }
    });
    bookingRows.appendChild(row);
  });
}

// ---------- init ----------

renderAuthState();
if (state.token) {
  loadResources();
  loadMyBookings();
}
