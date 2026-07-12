import os
import datetime
from flask import Flask, request, render_template_string, send_from_directory, redirect, url_for, session
# from werkzeug.utils import secure_filename  # not used anymore; we keep Unicode safely

UPLOAD_ROOT = "/opt/pasteboard/data"
PASSCODE = "xdxd"  # Change me!
HOST = "0.0.0.0"
PORT = 8000
SECRET_KEY = "now_replace_with_random_secret_key"

app = Flask(__name__)
app.secret_key = SECRET_KEY

# --- HTML Template ---
HTML_PAGE = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>LAN Pasteboard</title>
<style>
  body {
    font-family: Arial, sans-serif;
    margin: 20px;
    display: flex;
    flex-direction: column;
    align-items: center;
    min-height: 100vh;
    background-color: #f4f4f4;
  }
  .container {
    max-width: 800px;
    width: 100%;
    padding: 0 15px;
    box-sizing: border-box;
  }
  h1, h2 {
    text-align: center;
  }
  #dropzone {
    border: 3px dashed #aaa;
    padding: 30px;
    text-align: center;
    color: #555;
    margin-bottom: 15px;
    border-radius: 10px;
    transition: background-color 0.3s ease;
    width: 100%;
    box-sizing: border-box;
    cursor: pointer;
  }
  #dropzone.dragover {
    background-color: #eef8ff;
    border-color: #33aaff;
    color: #0077cc;
  }
  #previews, #recent {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    justify-content: center;
  }
  .preview {
    border: 1px solid #ccc;
    border-radius: 6px;
    padding: 6px;
    max-width: 180px;
    max-height: 130px;
    overflow: hidden;
    word-break: break-word;
    box-sizing: border-box;
    background-color: #fff;
  }
  .preview img, .preview video {
    max-width: 100%;
    max-height: 100px;
    display: block;
    margin-bottom: 4px;
    border-radius: 4px;
    object-fit: contain;
  }
  .preview pre {
    white-space: pre-wrap;
    max-height: 100px;
    overflow: auto;
    font-size: 12px;
    margin: 0;
  }
  textarea {
    width: 100%;
    height: 180px; /* bigger by default */
    margin: 10px 0;
    box-sizing: border-box;
    resize: vertical;
  }
  button {
    margin: 10px auto;
    padding: 8px 16px;
    display: block;
    width: fit-content;
  }
  a {
    display: block;
    text-align: center;
    margin-bottom: 15px;
    color: #0077cc;
    text-decoration: none;
  }
  a:hover {
    text-decoration: underline;
  }
  #periodSelector {
    display: flex;
    justify-content: center;
    gap: 10px;
    margin-bottom: 10px;
  }
  select {
    padding: 5px;
  }
  input[type="file"] {
    margin: 10px auto 20px;
    display: block;
  }
  @media (max-width: 600px) {
    body {
      margin: 10px;
    }
    .container {
      padding: 0 10px;
    }
    #dropzone {
      padding: 20px;
    }
    .preview {
      max-width: 100%;
      width: calc(50% - 10px);
      max-height: 150px;
    }
    .preview img, .preview video {
      max-height: 80px;
    }
    .preview pre {
      font-size: 11px;
    }
    textarea {
      height: 120px; /* also bigger on mobile */
    }
    button {
      width: 100%;
      padding: 10px;
    }
    #periodSelector {
      flex-direction: column;
      align-items: center;
    }
  }
</style>
</head>
<body>
<div class="container">
  <h1>LAN Pasteboard</h1>
  <p><a href="{{ url_for('logout') }}">Logout</a></p>

  <div id="dropzone" tabindex="0">
    Drag & Drop files here, or Paste images/text/videos (Ctrl+V / ⌘+V)
  </div>

  <!-- Fallback file input for mobile -->
  <input type="file" id="fileInput" multiple />

  <textarea id="textpaste" placeholder="Or paste text here"></textarea>
  <button id="sendText">Upload Text</button>

  <h2>Preview</h2>
  <div id="previews"></div>

  <div id="periodSelector">
    <select id="yearSelect"></select>
    <select id="monthSelect"></select>
    <button id="loadBtn">Load</button>
  </div>

  <h2 id="uploadsTitle">Recent Uploads (Last 50)</h2>
  <div id="recent"></div>
</div>

<script>
const dropzone = document.getElementById("dropzone");
const previews = document.getElementById("previews");
const textpaste = document.getElementById("textpaste");
const sendTextBtn = document.getElementById("sendText");
const recentDiv = document.getElementById("recent");
const uploadsTitle = document.getElementById("uploadsTitle");
const fileInput = document.getElementById("fileInput");

let currentYear = null;
let currentMonth = null;

// Show hover style
dropzone.addEventListener("dragover", e => {
  e.preventDefault();
  dropzone.classList.add("dragover");
});
dropzone.addEventListener("dragleave", e => {
  dropzone.classList.remove("dragover");
});
dropzone.addEventListener("drop", e => {
  e.preventDefault();
  dropzone.classList.remove("dragover");
  handleFiles(e.dataTransfer.files);
});
dropzone.addEventListener("click", () => fileInput.click());

// Paste handling
document.addEventListener("paste", e => {
  e.preventDefault();
  const items = (e.clipboardData || e.originalEvent?.clipboardData || window.clipboardData)?.items || [];
  let hasFiles = false;

  for (let item of items) {
    if (item.kind === "file") {
      const file = item.getAsFile();
      if (file) {
        hasFiles = true;
        handleFiles([file]);
      }
    }
  }

  if (!hasFiles) {
    // paste text fallback
    const text = (e.clipboardData || window.clipboardData).getData("text");
    if (text) {
      // Detect base64 image data URI (common on mobile pastes)
      if (text.startsWith("data:image")) {
        fetch(text)
          .then(res => res.blob())
          .then(blob => {
            const filename = "pasted_" + new Date().toISOString().replace(/[:.]/g, "") + ".png";
            const file = new File([blob], filename, { type: blob.type });
            previews.innerHTML = "";
            previewFile(file);
            uploadFiles([file]);
          })
          .catch(() => {
            // fallback to showing raw text
            textpaste.value = text;
            previews.innerHTML = `<div class="preview"><pre>${escapeHtml(text)}</pre></div>`;
          });
      } else {
        textpaste.value = text;
        previews.innerHTML = `<div class="preview"><pre>${escapeHtml(text)}</pre></div>`;
      }
    }
  }
});

fileInput.addEventListener("change", e => {
  const files = e.target.files;
  if (files.length > 0) {
    previews.innerHTML = "";
    handleFiles(files);
    fileInput.value = ""; // clear input to allow same file re-selection
  }
});

sendTextBtn.addEventListener("click", () => {
  const txt = textpaste.value.trim();
  if (!txt) return alert("Please enter some text");
  uploadText(txt);
});

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

function handleFiles(files) {
  previews.innerHTML = "";
  for (let file of files) {
    previewFile(file);
  }
  uploadFiles(files);
}

function previewFile(file) {
  const div = document.createElement("div");
  div.className = "preview";

  const timestamp = new Date().toISOString().replace(/[:.]/g, "");
  const filename = document.createElement("div");
  filename.textContent = file.name || `pasted_${timestamp}` + getExt(file.type);

  if (file.type.startsWith("image/")) {
    const img = document.createElement("img");
    img.src = URL.createObjectURL(file);
    div.appendChild(img);
  } else if (file.type.startsWith("video/")) {
    const video = document.createElement("video");
    video.src = URL.createObjectURL(file);
    video.controls = true;
    div.appendChild(video);
  } else {
    // generic file icon and info
    div.innerHTML = `<strong>${escapeHtml(file.name || "file")}</strong><br>Size: ${file.size} bytes`;
  }
  div.appendChild(filename);
  previews.appendChild(div);
}

function getExt(type) {
  if (type.startsWith("image/png")) return ".png";
  if (type.startsWith("image/jpeg")) return ".jpg";
  if (type.startsWith("image/gif")) return ".gif";
  if (type.startsWith("image/webp")) return ".webp";
  if (type.startsWith("video/")) return ".mp4";
  return ".bin";
}

function uploadFiles(files) {
  const formData = new FormData();
  const timestamp = new Date().toISOString().replace(/[:.]/g, "");
  for (let i = 0; i < files.length; i++) {
    let file = files[i];
    let filename = file.name || `pasted_${timestamp}_${i}` + getExt(file.type);
    formData.append("file", file, filename);
  }
  fetch("/upload_file", {
    method: "POST",
    body: formData,
  }).then(resp => {
    if (!resp.ok) alert("Upload failed");
    else {
      textpaste.value = "";
      previews.innerHTML = "";
      fetchFiles();
    }
  });
}

function uploadText(text) {
  fetch("/upload_text", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text }),
  }).then(resp => {
    if (!resp.ok) alert("Upload failed");
    else {
      textpaste.value = "";
      previews.innerHTML = "";
      fetchFiles();
    }
  });
}

function fetchPeriods() {
  fetch("/list_periods").then(r => r.json()).then(data => {
    const periods = data.periods;
    const yearSelect = document.getElementById("yearSelect");
    yearSelect.innerHTML = '<option value="">All (Recent)</option>';
    periods.forEach(p => {
      const opt = document.createElement("option");
      opt.value = p.year;
      opt.textContent = p.year;
      yearSelect.appendChild(opt);
    });
    yearSelect.addEventListener("change", updateMonths);
    updateMonths();
  });
}

function updateMonths() {
  const year = document.getElementById("yearSelect").value;
  const monthSelect = document.getElementById("monthSelect");
  if (!year) {
    monthSelect.innerHTML = '<option value="">--</option>';
    monthSelect.disabled = true;
    return;
  }
  monthSelect.disabled = false;
  fetch("/list_periods").then(r => r.json()).then(data => {
    const p = data.periods.find(pp => pp.year === year);
    monthSelect.innerHTML = "";
    p.months.forEach(m => {
      const opt = document.createElement("option");
      opt.value = m;
      opt.textContent = m;
      monthSelect.appendChild(opt);
    });
  });
}

document.getElementById("loadBtn").addEventListener("click", () => {
  currentYear = document.getElementById("yearSelect").value;
  currentMonth = document.getElementById("monthSelect").value;
  fetchFiles();
});

function fetchFiles() {
  let url = "/list_files";
  let title = "Recent Uploads (Last 50)";
  if (currentYear && currentMonth) {
    url += `?year=${currentYear}&month=${currentMonth}`;
    title = `Uploads for ${currentYear}-${currentMonth}`;
  }
  uploadsTitle.textContent = title;
  fetch(url).then(r => r.json()).then(data => {
    recentDiv.innerHTML = "";
    if (data.files.length === 0) {
      recentDiv.textContent = "No uploads yet";
      return;
    }
    for (let file of data.files) {
      const a = document.createElement("a");
      a.href = file.url;
      a.target = "_blank";
      a.style.textDecoration = "none";
      const div = document.createElement("div");
      div.className = "preview";
      if (file.type === "text") {
        div.innerHTML = `<pre>${escapeHtml(file.content)}</pre><small>${file.time}</small>`;
      } else if (file.type === "image") {
        const img = document.createElement("img");
        img.src = file.url;
        div.appendChild(img);
        const lbl = document.createElement("div");
        lbl.textContent = `${file.filename} (${file.time})`;
        div.appendChild(lbl);
      } else if (file.type === "video") {
        const vid = document.createElement("video");
        vid.src = file.url;
        vid.controls = true;
        div.appendChild(vid);
        const lbl = document.createElement("div");
        lbl.textContent = `${file.filename} (${file.time})`;
        div.appendChild(lbl);
      } else {
        div.innerHTML = `<strong>${escapeHtml(file.filename)}</strong><br>${file.time}`;
      }
      a.appendChild(div);
      recentDiv.appendChild(a);
    }
  });
}

fetchPeriods();
fetchFiles();

</script>
</body>
</html>
"""

LOGIN_PAGE = """
<!doctype html>
<title>Login</title>
<style>
  body {
    font-family: Arial, sans-serif;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
    margin: 0;
    background-color: #f4f4f4;
  }
  h2 {
    text-align: center;
  }
  form {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
  }
  input[type="password"] {
    padding: 8px;
    width: 200px;
    border: 1px solid #ccc;
    border-radius: 4px;
  }
  input[type="submit"] {
    padding: 8px 16px;
    background-color: #0077cc;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
  }
  input[type="submit"]:hover {
    background-color: #005fa3;
  }
  @media (max-width: 600px) {
    input[type="password"] {
      width: 100%;
      max-width: 250px;
    }
  }
</style>
<h2>Enter Passcode</h2>
<form method="post">
  <input type="password" name="passcode" autofocus>
  <input type="submit" value="Login">
</form>
"""

def ensure_dir(path):
    os.makedirs(path, exist_ok=True)

def file_type_from_ext(filename):
    # Keep support for images/videos/text; everything else is 'file'
    ext = filename.lower().rsplit('.', 1)[-1] if '.' in filename else ''
    if ext in ('png','jpg','jpeg','gif','webp'):
        return 'image'
    if ext in ('mp4','webm','ogg'):
        return 'video'
    if ext == 'txt':
        return 'text'
    return 'file'

def list_periods():
    periods = []
    for y in os.listdir(UPLOAD_ROOT):
        year_path = os.path.join(UPLOAD_ROOT, y)
        if os.path.isdir(year_path) and y.isdigit():
            months = [m for m in os.listdir(year_path) if os.path.isdir(os.path.join(year_path, m)) and m.isdigit()]
            months.sort(key=int, reverse=True)
            periods.append({"year": y, "months": months})
    periods.sort(key=lambda x: int(x["year"]), reverse=True)
    return periods

def list_files(year=None, month=None, limit=10):
    def build_record(full_path, y, m, filename):
        stat = os.stat(full_path)
        mtime = datetime.datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d %H:%M:%S")
        ftype = file_type_from_ext(filename)
        content = None
        if ftype == 'text':
            try:
                with open(full_path, encoding='utf-8') as f:
                    content = f.read(500)
            except:
                content = None
        return {
            "year": y,
            "month": m,
            "filename": filename,
            "url": f"/uploads/{y}/{m}/{filename}",
            "time": mtime,
            "type": ftype,
            "content": content,
        }

    if year and month:
        folder = os.path.join(UPLOAD_ROOT, year, month)
        if not os.path.exists(folder):
            return []
        files = []
        for fn in os.listdir(folder):
            full = os.path.join(folder, fn)
            if os.path.isfile(full):
                files.append(build_record(full, year, month, fn))
        files.sort(key=lambda x: x["time"], reverse=True)
        return files
    else:
        files = []
        for root, _, filenames in os.walk(UPLOAD_ROOT):
            for fn in filenames:
                full = os.path.join(root, fn)
                rel = os.path.relpath(full, UPLOAD_ROOT)
                parts = rel.split(os.sep)
                if len(parts) < 3:
                    continue
                y, m, filename = parts
                files.append(build_record(full, y, m, filename))
        files.sort(key=lambda x: x["time"], reverse=True)
        return files[:limit]

def safe_unicode_filename(name: str) -> str:
    """
    Keep Unicode characters, but prevent traversal and illegal chars.
    - Strips directory components
    - Removes nulls and control chars
    - Replaces slashes and backslashes with underscores
    - Trims whitespace and leading/trailing dots
    - Limits length to a sane max
    """
    # Base name only (no directories)
    name = os.path.basename(name or "")
    # Remove null byte
    name = name.replace("\x00", "")
    # Replace path separators
    name = name.replace("/", "_").replace("\\", "_")
    # Collapse whitespace
    name = " ".join(name.split())
    # Strip surrounding dots/spaces
    name = name.strip().strip(".")
    # Fallback if empty
    if not name:
        name = "file"
    # Limit total length to avoid filesystem issues (keep extension intact)
    base, ext = os.path.splitext(name)
    max_total = 200  # safe cap
    if len(name) > max_total:
        max_base = max_total - len(ext)
        base = base[:max_base]
    return f"{base}{ext}"

@app.route("/", methods=["GET", "POST"])
def index():
    if not session.get("logged_in"):
        return redirect("/login")
    return render_template_string(HTML_PAGE)

@app.route("/login", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        passcode = request.form.get("passcode", "")
        if passcode == PASSCODE:
            session["logged_in"] = True
            return redirect("/")
        else:
            return render_template_string(LOGIN_PAGE + "<p style='color:red'>Incorrect passcode</p>")
    return render_template_string(LOGIN_PAGE)

@app.route("/logout")
def logout():
    session.pop("logged_in", None)
    return redirect("/login")

@app.route("/upload_file", methods=["POST"])
def upload_file():
    if not session.get("logged_in"):
        return "Unauthorized", 401

    files = request.files.getlist("file")
    if not files:
        return "No file", 400

    now = datetime.datetime.now()
    year = now.strftime("%Y")
    month = now.strftime("%m")
    upload_dir = os.path.join(UPLOAD_ROOT, year, month)
    ensure_dir(upload_dir)

    timestamp = now.strftime("%Y%m%d_%H%M%S%f")

    for f in files:
        # Preserve full Unicode name safely
        original_name = safe_unicode_filename(f.filename) if f and f.filename else "pasted_file"
        base, ext = os.path.splitext(original_name)

        # Always append timestamp before the extension to avoid overwrites
        filename = f"{base}_{timestamp}{ext}"

        save_path = os.path.join(upload_dir, filename)
        f.save(save_path)

    return "OK"

@app.route("/upload_text", methods=["POST"])
def upload_text():
    if not session.get("logged_in"):
        return "Unauthorized", 401

    data = request.get_json()
    if not data or "text" not in data or not data["text"].strip():
        return "No text", 400

    now = datetime.datetime.now()
    year = now.strftime("%Y")
    month = now.strftime("%m")
    upload_dir = os.path.join(UPLOAD_ROOT, year, month)
    ensure_dir(upload_dir)

    filename = f"paste_{now.strftime('%Y%m%d_%H%M%S%f')}.txt"
    save_path = os.path.join(upload_dir, filename)
    with open(save_path, "w", encoding="utf-8") as f:
        f.write(data["text"])

    return "OK"

@app.route("/list_periods")
def list_periods_api():
    if not session.get("logged_in"):
        return "Unauthorized", 401
    return {"periods": list_periods()}

@app.route("/list_files")
def list_files_api():
    if not session.get("logged_in"):
        return "Unauthorized", 401

    year = request.args.get("year")
    month = request.args.get("month")
    files = list_files(year, month)
    return {"files": files}

@app.route("/uploads/<year>/<month>/<filename>")
def serve_upload(year, month, filename):
    if not session.get("logged_in"):
        return "Unauthorized", 401
    return send_from_directory(os.path.join(UPLOAD_ROOT, year, month), filename, as_attachment=False)

if __name__ == "__main__":
    ensure_dir(UPLOAD_ROOT)
    print(f"Starting pasteboard server on {HOST}:{PORT}")
    app.run(host=HOST, port=PORT)
