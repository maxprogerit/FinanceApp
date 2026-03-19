#!/bin/bash
# ============================================================
# Smart Finance Dashboard — Production Deployment Script
# Run on your Ubuntu/Debian VPS as root or sudo user
# ============================================================
set -e

APP_NAME="financeapp"
APP_USER="financeapp"
APP_DIR="/opt/financeapp"
LOG_DIR="/var/log/financeapp"
JAR_NAME="dashboard-1.0.0.jar"

echo "=== Smart Finance Dashboard Deployment ==="

# ── 1. Install system dependencies ───────────────────────────────────────────
echo "[1/8] Installing dependencies..."
apt-get update -q
apt-get install -y -q openjdk-17-jre-headless postgresql nginx certbot python3-certbot-nginx

# ── 2. Create application user ────────────────────────────────────────────────
echo "[2/8] Creating application user..."
id -u $APP_USER &>/dev/null || useradd -r -s /bin/false $APP_USER
mkdir -p $APP_DIR $LOG_DIR
chown $APP_USER:$APP_USER $APP_DIR $LOG_DIR

# ── 3. Set up PostgreSQL ──────────────────────────────────────────────────────
echo "[3/8] Configuring PostgreSQL..."
sudo -u postgres psql -c "CREATE USER financeapp WITH PASSWORD 'CHANGE_ME';" 2>/dev/null || true
sudo -u postgres psql -c "CREATE DATABASE financedb OWNER financeapp;" 2>/dev/null || true

# ── 4. Build the application ──────────────────────────────────────────────────
echo "[4/8] Building application JAR..."
./mvnw clean package -DskipTests -Pprod

# ── 5. Deploy JAR and configuration ──────────────────────────────────────────
echo "[5/8] Deploying files..."
cp target/$JAR_NAME $APP_DIR/
cp deploy/financeapp.service /etc/systemd/system/

# Copy .env if it exists (you must create this manually with real secrets)
if [ -f ".env" ]; then
    cp .env $APP_DIR/.env
    chmod 600 $APP_DIR/.env
    chown $APP_USER:$APP_USER $APP_DIR/.env
fi

chown $APP_USER:$APP_USER $APP_DIR/$JAR_NAME

# ── 6. Set up Nginx ───────────────────────────────────────────────────────────
echo "[6/8] Configuring Nginx..."
cp deploy/nginx.conf /etc/nginx/sites-available/$APP_NAME
ln -sf /etc/nginx/sites-available/$APP_NAME /etc/nginx/sites-enabled/$APP_NAME
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

# ── 7. Enable and start service ───────────────────────────────────────────────
echo "[7/8] Starting application service..."
systemctl daemon-reload
systemctl enable $APP_NAME
systemctl restart $APP_NAME

# ── 8. Status check ───────────────────────────────────────────────────────────
echo "[8/8] Checking service status..."
sleep 5
systemctl status $APP_NAME --no-pager

echo ""
echo "=== Deployment complete! ==="
echo "Next steps:"
echo "  1. Edit /opt/financeapp/.env with real secrets"
echo "  2. Run: certbot --nginx -d yourdomain.com"
echo "  3. Update nginx.conf with your actual domain"
echo "  4. Visit https://yourdomain.com"
