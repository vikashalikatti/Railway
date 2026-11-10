# Running Railway in Docker and Kubernetes

This guide takes you from a Windows PC with nothing installed to the whole app (MySQL, the API and
the web app) running in containers, first with **Docker Compose** and then on **Kubernetes** with
health checks, rolling updates and automatic scaling.

```
 Browser ──► frontend (nginx, web app)  ──/customer, /admin──►  backend (Spring Boot)  ──►  MySQL
             http://localhost:8081                               port 8080                 port 3306
```

The web app's nginx forwards API calls to the backend, so the browser only ever talks to one
address (no CORS setup needed).

Both repositories need to be side by side, for example:

```
D:\project_railway_full\
├── Railway\              ← this repository (backend): Dockerfile, docker-compose.yml, k8s\
└── Railway_Frontend\     ← the web app: its own Dockerfile
```

---

## 1. Install Docker Desktop (Windows 10 / 11)

1. **Turn on virtualization** in the BIOS if it's off (Task Manager → Performance → CPU shows
   "Virtualization: Enabled").
2. **Install WSL 2** (Docker runs its Linux engine there). In PowerShell **as Administrator**:
   ```powershell
   wsl --install
   ```
   Restart the PC when asked.
3. **Install Docker Desktop**:
   ```powershell
   winget install -e --id Docker.DockerDesktop
   ```
   (or download it from docker.com). Start Docker Desktop, accept the terms, and keep
   **Settings → General → Use the WSL 2 based engine** ticked.
4. **Check it works** (a new PowerShell window):
   ```powershell
   docker version
   docker run --rm hello-world
   ```

---

## 2. Run everything with Docker Compose

In the backend folder:

```powershell
cd D:\project_railway_full\Railway
copy .env.example .env
notepad .env
```

Fill in `.env`:

| Setting | What to put |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | Any user name (not `root`) and password: MySQL in Docker is created with them |
| `MYSQL_ROOT_PASSWORD` | Any password for MySQL's root user |
| `JWT_SECRET` | Long random text (the PowerShell line in the file makes one) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Your Gmail address and its App Password |
| `FRONTEND_DIR` | Path to the web app's folder from here, e.g. `../Railway_Frontend` |
| `WEB_PORT` | Port for the web app (default 8081) |

`.env` is ignored by git, so your passwords stay on your PC.

Build and start (the first build downloads Java, Maven, Node and MySQL images, so it takes a few minutes):

```powershell
docker compose up -d --build
docker compose ps          # all three should become "healthy"
docker compose logs -f backend    # Ctrl+C to stop watching
```

Open **http://localhost:8081**, create the admin at `/console/create`, log in and go to
**Import trains → Import Karnataka trains**.

Everyday commands:

| | |
|---|---|
| `docker compose stop` / `docker compose start` | Stop / start, keeping everything |
| `docker compose up -d --build` | Rebuild after you change the code |
| `docker compose logs -f frontend` | Watch a service's log |
| `docker compose exec mysql mysql -u railway -p railway_boot_api` | MySQL prompt |
| `docker compose down` | Remove the containers (the database is kept) |
| `docker compose down -v` | Remove everything **including the database** |

MySQL Workbench can connect to `127.0.0.1`, port **3307**, with `DB_USERNAME` / `DB_PASSWORD`.
The API is also on http://localhost:8080 for Postman.

Back up the database:
```powershell
docker compose exec mysql sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" railway_boot_api' > backup.sql
```

---

## 3. Kubernetes

Docker Compose runs containers on one computer. **Kubernetes** runs them on a cluster and keeps
them in the state you describe: it restarts crashed containers, replaces them one by one when you
update, spreads traffic over copies, and adds copies when they get busy.

| Word | Meaning here |
|---|---|
| **Pod** | One running container (one backend, one nginx, MySQL) |
| **Deployment** | "Keep N backend pods running this image": restarts and rolling updates |
| **StatefulSet** | Like a Deployment, but the pod keeps its own disk (MySQL) |
| **Service** | A fixed name and address in front of pods (`backend:8080`, `mysql:3306`) |
| **ConfigMap / Secret** | Settings / passwords given to pods as environment variables |
| **HorizontalPodAutoscaler** | Adds or removes pods based on CPU use |
| **Probe** | Health check: a pod gets traffic only when ready, and is restarted if it stops answering |

The manifests are in `k8s/`.

### 3.1 Turn on Kubernetes

**Docker Desktop** (easiest): Settings → **Kubernetes** → **Enable Kubernetes**. If it asks for a
cluster type, choose **kubeadm** (single node): then the images you build with `docker build` are
visible to the cluster. (With **kind**, the cluster has its own image store and can't see them.)
Apply & restart, wait for the green "Kubernetes running", then:

```powershell
kubectl config use-context docker-desktop
kubectl get nodes          # one node, STATUS Ready
```

`kubectl` comes with Docker Desktop.

**Or minikube**:
```powershell
winget install -e --id Kubernetes.minikube
minikube start --driver=docker --cpus=4 --memory=6g
minikube addons enable metrics-server
```

### 3.2 metrics-server (needed for autoscaling)

minikube: done above. Docker Desktop (from the backend folder):
```powershell
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl -n kube-system patch deployment metrics-server --type=json --patch-file k8s/extras/metrics-server-patch.json
```
(The patch lets it read Docker Desktop's local node, which has a self-signed certificate.)
After a minute, `kubectl top nodes` shows CPU and memory.

### 3.3 Build the images

```powershell
cd D:\project_railway_full\Railway
docker compose build                 # uses .env from step 2; builds railway-backend:1.13.0 and railway-frontend:1.17.0
```
minikube only, copy them into the cluster:
```powershell
minikube image load railway-backend:1.13.0
minikube image load railway-frontend:1.17.0
```

### 3.4 Passwords and settings

Create the namespace and the Secret (PowerShell; the backtick continues the line):
```powershell
kubectl apply -f k8s/namespace.yaml
kubectl -n railway create secret generic railway-secrets `
  --from-literal=DB_USERNAME=railway `
  --from-literal=DB_PASSWORD=choose-a-password `
  --from-literal=MYSQL_ROOT_PASSWORD=choose-another `
  --from-literal=JWT_SECRET=long-random-text-32-characters-or-more `
  --from-literal=MAIL_PASSWORD=your-gmail-app-password
```
(Or copy `k8s/secret.example.yaml` to `k8s/secret.yaml`, fill it in and `kubectl apply -f k8s/secret.yaml`.
`secret.yaml` is ignored by git.)

Put your Gmail address in `k8s/config.yaml` (`SPRING_MAIL_USERNAME`).

### 3.5 Deploy

```powershell
kubectl apply -k k8s/
kubectl -n railway get pods -w       # wait for every pod to be 1/1 Running, then Ctrl+C
```

Open **http://localhost:8081** (minikube: run `minikube tunnel` in another window first). Create the
admin and import the Karnataka trains as with Docker Compose.

See what's running:
```powershell
kubectl -n railway get all
kubectl -n railway logs deploy/backend -f
kubectl -n railway describe pod <pod-name>       # why a pod isn't starting
```

### 3.6 Scaling

**By hand** (the web app has no autoscaler minimum to fight):
```powershell
kubectl -n railway scale deployment frontend --replicas=3
kubectl -n railway get pods -l app=frontend
```

**Automatically**: both Deployments have a HorizontalPodAutoscaler. The backend starts with 1 pod
(the first start creates the tables, which is best done by one pod). Once it's up, raise its
minimum: in `k8s/backend.yaml` set `minReplicas: 2` and run `kubectl apply -k k8s/` again.

Watch the autoscaler:
```powershell
kubectl -n railway get hpa -w
```
It adds backend pods (up to 4) when their average CPU passes 70% of what they requested, and
removes them a few minutes after the load goes away.

To see it happen, put some load on the API from inside the cluster (set `RATE_LIMIT: "false"` in
`k8s/config.yaml` and `kubectl apply -k k8s/` first, or the rate limits answer most requests with 429):
```powershell
kubectl -n railway run load --rm -it --image=busybox:1.36 --restart=Never -- sh -c "while true; do wget -q -O /dev/null 'http://backend:8080/customer/stations?q=Bengaluru'; done"
```
Stop it with Ctrl+C.

### 3.7 Updating without downtime

Build a new version with a new tag, then point the Deployment at it:
```powershell
docker build -t railway-backend:1.13.1 .
kubectl -n railway set image deployment/backend backend=railway-backend:1.13.1
kubectl -n railway rollout status deployment/backend
```
Kubernetes starts a new pod, waits until its readiness check passes, and only then stops an old
one. If something is wrong:
```powershell
kubectl -n railway rollout undo deployment/backend
```
(Also change the tag in `k8s/backend.yaml`, so the next `kubectl apply -k k8s/` keeps it.)

### 3.8 Other useful commands

| | |
|---|---|
| `kubectl -n railway port-forward svc/backend 8080:8080` | The API on http://localhost:8080 (Postman, Swagger UI) |
| `kubectl -n railway port-forward svc/mysql 3307:3306` | MySQL for Workbench on port 3307 |
| `kubectl -n railway exec -it mysql-0 -- mysql -u root -p` | MySQL prompt inside the cluster |
| `kubectl -n railway rollout restart deployment/backend` | Restart the backend pods one by one |
| `kubectl -n railway top pods` | CPU and memory per pod |
| `kubectl delete -k k8s/` | Remove the app (the database disk stays) |
| `kubectl -n railway delete pvc data-mysql-0` | Delete the database disk too |

### 3.9 Good to know when running several backend pods

- **Log-ins work on every pod** because they all use the same `JWT_SECRET` (from the Secret). If
  it were missing, each pod would make its own and log-ins would fail at random.
- **Bookings, charts and refunds are safe on several pods**: seats, charts and payments are locked
  in MySQL, so two pods never give the same berth or prepare the same chart twice.
- **Rate limits are counted per pod**, so with 3 pods a client can make up to 3 times the limit.
- **Import progress** lives on the pod running the import. If the import page says no import is
  running while one is, the page asked another pod: refresh after a moment.
- **MySQL is a single pod.** For real use, use a managed MySQL service and change
  `SPRING_DATASOURCE_URL` in `k8s/config.yaml`.

### 3.10 On a real server

- Push the images to a registry (Docker Hub, GitHub Container Registry…) with `docker push` and
  use those image names in the manifests.
- Put HTTPS in front with a Gateway API or Ingress controller. The community **ingress-nginx**
  controller was retired in March 2026 and gets no more security fixes, so pick another one
  (the Kubernetes project recommends moving to Gateway API).
- Don't open MySQL or the backend to the internet: only the web app's Service needs to be reachable.

---

## 4. Problems

| What you see | What to do |
|---|---|
| `ImagePullBackOff` / `ErrImageNeverPull` | The cluster can't see your image: Docker Desktop must use **kubeadm**, or on minikube run `minikube image load …` |
| backend `CrashLoopBackOff` | `kubectl -n railway logs deploy/backend --previous`: usually a wrong DB password, or `MAIL_PASSWORD` / `JWT_SECRET` missing from the Secret |
| backend restarts while MySQL starts | Normal on the very first start (MySQL takes a minute); it settles by itself |
| `Pending` pod, "Insufficient memory" | Give Docker Desktop more memory (Settings → Resources), or minikube `--memory=6g` |
| `docker compose` says a variable is required | Fill it in `.env` |
| Port 8081 / 8080 / 3307 already in use | Change `WEB_PORT` in `.env`, or stop what uses the port |
| HPA shows `<unknown>` targets | metrics-server isn't running yet: see 3.2 |
| The very first `docker compose up` stops at "Downloading" | Slow network: run it again, finished layers are kept |
