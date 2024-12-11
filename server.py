from flask import Flask, request, jsonify, send_from_directory
import os
import sqlite3
from werkzeug.security import generate_password_hash, check_password_hash

app = Flask(__name__)

# 设置上传文件的保存目录
BASE_UPLOAD_FOLDER = 'uploads'
os.makedirs(BASE_UPLOAD_FOLDER, exist_ok=True)

# SQLite 数据库初始化
def init_db():
    conn = sqlite3.connect('backup.db')
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL
        )
    ''')
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER,
            filename TEXT,
            filepath TEXT,
            FOREIGN KEY (user_id) REFERENCES users (id)
        )
    ''')
    conn.commit()
    conn.close()

@app.route('/register', methods=['POST'])
def register():
    data = request.json
    username = data.get('username')
    password = data.get('password')

    hashed_password = generate_password_hash(password)
    
    conn = sqlite3.connect('backup.db')
    cursor = conn.cursor()
    try:
        cursor.execute('INSERT INTO users (username, password) VALUES (?, ?)', (username, hashed_password))
        conn.commit()
        return jsonify({'message': '注册成功'}), 201
    except sqlite3.IntegrityError:
        return jsonify({'message': '用户名已存在'}), 400
    finally:
        conn.close()

@app.route('/login', methods=['POST'])
def login():
    data = request.json
    username = data.get('username')
    password = data.get('password')

    conn = sqlite3.connect('backup.db')
    cursor = conn.cursor()
    cursor.execute('SELECT * FROM users WHERE username = ?', (username,))
    user = cursor.fetchone()
    
    if user and check_password_hash(user[2], password):
        return jsonify({'message': '登录成功', 'user_id': user[0]}), 200
    return jsonify({'message': '用户名或密码错误'}), 401

@app.route('/upload', methods=['POST'])
def upload_file():
    if 'file' not in request.files:
        return '没有文件上传', 400

    file = request.files['file']
    user_id = request.form.get('user_id')

    if file.filename == '':
        return '没有选择文件', 400

    # 创建用户目录
    user_folder = os.path.join(BASE_UPLOAD_FOLDER, str(user_id))
    os.makedirs(user_folder, exist_ok=True)

    # 重命名文件以避免冲突
    filename = file.filename
    file_path = os.path.join(user_folder, filename)
    counter = 1
    while os.path.exists(file_path):
        name, ext = os.path.splitext(filename)
        file_path = os.path.join(user_folder, f"{name}_{counter}{ext}")
        counter += 1

    file.save(file_path)

    # 保存文件信息到数据库
    conn = sqlite3.connect('backup.db')
    cursor = conn.cursor()
    cursor.execute('INSERT INTO files (user_id, filename, filepath) VALUES (?, ?, ?)', (user_id, filename, file_path))
    conn.commit()
    conn.close()

    return '文件上传成功', 200

@app.route('/download/<int:user_id>/<filename>', methods=['GET'])
def download_file(user_id, filename):
    user_folder = os.path.join(BASE_UPLOAD_FOLDER, str(user_id))
    return send_from_directory(user_folder, filename, as_attachment=True)

@app.route('/files/<int:user_id>', methods=['GET'])
def list_files(user_id):
    user_folder = os.path.join(BASE_UPLOAD_FOLDER, str(user_id))
    if not os.path.exists(user_folder):
        return jsonify([])

    files = os.listdir(user_folder)
    return jsonify(files)

if __name__ == '__main__':
    init_db()
    app.run(host='0.0.0.0', port=5000)
