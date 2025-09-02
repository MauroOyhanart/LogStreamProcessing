#!/bin/bash

# Amazon Linux 2023 user_data for t3.small (x86_64)
set -euxo pipefail

# Log to console and file
exec > >(tee -a /var/log/user-data.log) 2>&1
echo "=== $(date -u) :: bootstrap start for ${instance_name} ==="

# Wait for package manager locks (just in case)
for i in {1..60}; do
  if fuser /var/run/dnf.pid >/dev/null 2>&1; then
    echo "dnf busy; waiting... ($i)"; sleep 2
  else
    break
  fi
done

# Base refresh
dnf -y update

# Tools (NO 'curl' here to avoid conflicts with curl-minimal)
dnf -y install git wget unzip java-17-amazon-corretto-headless maven dnf-plugins-core

# -------- Docker (pin Fedora 38 path) --------
cat >/etc/yum.repos.d/docker-ce.repo <<'EOF'
[docker-ce-stable]
name=Docker CE Stable - Fedora 38
baseurl=https://download.docker.com/linux/fedora/38/$basearch/stable
enabled=1
gpgcheck=1
gpgkey=https://download.docker.com/linux/fedora/gpg
EOF

dnf clean all
dnf makecache
dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin --allowerasing
systemctl enable --now docker
usermod -aG docker ec2-user || true

# -------- GitHub CLI (gh) --------
dnf config-manager --add-repo https://cli.github.com/packages/rpm/gh-cli.repo
dnf clean all
dnf makecache --refresh
dnf -y install gh

# -------- AWS CLI v2 --------
tmpdir="$(mktemp -d)"
pushd "$tmpdir"
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip
unzip -q awscliv2.zip
./aws/install
popd
rm -rf "$tmpdir"

# JAVA_HOME for ec2-user
grep -q 'JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto' /home/ec2-user/.bashrc || {
  echo "export JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto" >> /home/ec2-user/.bashrc
  echo "export PATH=$PATH:$JAVA_HOME/bin" >> /home/ec2-user/.bashrc
}

# App dir
mkdir -p /opt/logstreamprocessing
chown ec2-user:ec2-user /opt/logstreamprocessing

# Sanity (non-fatal)
echo "=== Verification ==="
docker --version || true
docker compose version || true
java -version || true
mvn -version || true
aws --version || true
gh --version || true
docker run --rm hello-world || true

# Success marker
echo ok > /var/local/bootstrap.done
echo "=== $(date -u) :: bootstrap complete for ${instance_name} ==="
exit 0
