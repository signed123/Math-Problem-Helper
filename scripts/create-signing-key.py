#!/usr/bin/env python3
"""Generate a personal signing key without logging its password or embedding it in source."""
from pathlib import Path
import os,secrets,shutil,subprocess

root=Path(__file__).resolve().parent.parent
directory=root.parent/'.signing'
directory.mkdir(mode=0o700,exist_ok=True)
directory.chmod(0o700)
store=directory/'math-tutor-release.jks'
password_file=directory/'math-tutor-release.password'
keytool=shutil.which('keytool')
task_jdk=root.parent/'.toolchains/jdk/bin/keytool'
if task_jdk.is_file(): keytool=str(task_jdk)
if not keytool: raise SystemExit('需要安装 JDK 17（包含 keytool）')
if not store.exists():
    password_file.write_text(secrets.token_urlsafe(32))
    password_file.chmod(0o600)
    subprocess.run([keytool,'-genkeypair','-keystore',str(store),'-storetype','JKS',
                    '-storepass:file',str(password_file),'-keypass:file',str(password_file),
                    '-alias','math-tutor','-keyalg','RSA','-keysize','3072','-validity','10000',
                    '-dname','CN=Math Tutor Personal Build'],check=True,capture_output=True)
    store.chmod(0o600)
elif not password_file.exists(): raise SystemExit('已有签名密钥但缺少口令文件，未覆盖密钥')
password=password_file.read_text().strip()
config=root/'signing.properties'
config.write_text(f'storeFile={store}\nstorePassword={password}\nkeyAlias=math-tutor\nkeyPassword={password}\n')
config.chmod(0o600)
print('个人签名配置已准备；密钥保存在工程同级 .signing 私有目录。请单独备份。')
