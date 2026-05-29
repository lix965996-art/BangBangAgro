# coding:utf-8
"""
果蔬成熟度检测 Flask API 服务
"""
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
from ultralytics import YOLO
import cv2
import numpy as np
import os
import uuid
from datetime import datetime
import base64

app = Flask(__name__)
CORS(app)  # 允许跨域请求

# 配置
MODEL_PATH = 'models/best.pt'
UPLOAD_FOLDER = 'api_uploads'
RESULT_FOLDER = 'api_results'

# 类别名称
CLASS_NAMES = {0: 'Riped', 1: 'UnRiped'}
CH_NAMES = {0: '成熟', 1: '未成熟'}

# 允许上传的图片扩展名白名单
ALLOWED_IMAGE_EXTENSIONS = {'jpg', 'jpeg', 'png', 'bmp', 'webp', 'tiff'}

def validate_image_file(file):
    """校验上传文件是否为允许的图片类型"""
    if not file or not file.filename:
        return False, '未找到上传的文件'
    if '.' not in file.filename:
        return False, '文件名缺少扩展名'
    ext = file.filename.rsplit('.', 1)[-1].lower()
    if ext not in ALLOWED_IMAGE_EXTENSIONS:
        return False, f'不支持的文件类型: .{ext}'
    return True, ext

# 创建文件夹
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(RESULT_FOLDER, exist_ok=True)

# 加载模型
print("正在加载YOLO模型...")
model = YOLO(MODEL_PATH, task='detect')
print("模型加载完成!")


@app.route('/api/detect', methods=['POST'])
def detect():
    """
    果蔬成熟度检测接口
    接收图片文件，返回检测结果
    """
    try:
        # 检查是否有文件
        if 'file' not in request.files:
            return jsonify({
                'code': '400',
                'msg': '未找到上传的文件',
                'data': None
            })
        
        file = request.files['file']
        if file.filename == '':
            return jsonify({
                'code': '400',
                'msg': '文件名为空',
                'data': None
            })

        # 校验文件类型
        valid, result = validate_image_file(file)
        if not valid:
            return jsonify({
                'code': '400',
                'msg': result,
                'data': None
            })
        file_ext = result
        unique_name = f"{uuid.uuid4().hex}.{file_ext}"
        
        # 保存上传的文件
        upload_path = os.path.join(UPLOAD_FOLDER, unique_name)
        file.save(upload_path)
        
        # 读取图片
        img = cv2.imdecode(np.fromfile(upload_path, dtype=np.uint8), cv2.IMREAD_COLOR)
        if img is None:
            return jsonify({
                'code': '400',
                'msg': '无法读取图片文件',
                'data': None
            })
        
        # 进行检测
        results = model(img, conf=0.5)
        
        # 解析检测结果
        detections = []
        riped_count = 0
        unriped_count = 0
        
        for result in results:
            boxes = result.boxes
            for box in boxes:
                cls_id = int(box.cls[0])
                conf = float(box.conf[0])
                xyxy = box.xyxy[0].tolist()
                
                # 统计数量
                if cls_id == 0:
                    riped_count += 1
                else:
                    unriped_count += 1
                
                detections.append({
                    'class_id': cls_id,
                    'class_name': CLASS_NAMES.get(cls_id, 'Unknown'),
                    'class_name_ch': CH_NAMES.get(cls_id, '未知'),
                    'confidence': round(conf * 100, 2),
                    'bbox': {
                        'x1': int(xyxy[0]),
                        'y1': int(xyxy[1]),
                        'x2': int(xyxy[2]),
                        'y2': int(xyxy[3])
                    }
                })
        
        # 绘制结果图片
        result_img = results[0].plot()
        result_name = f"result_{unique_name}"
        result_path = os.path.join(RESULT_FOLDER, result_name)
        cv2.imencode('.jpg', result_img)[1].tofile(result_path)
        
        # 将结果图片转为base64
        with open(result_path, 'rb') as f:
            result_base64 = base64.b64encode(f.read()).decode('utf-8')
        
        # 计算成熟度比例
        total = riped_count + unriped_count
        riped_ratio = round(riped_count / total * 100, 2) if total > 0 else 0
        
        # 返回结果
        return jsonify({
            'code': '200',
            'msg': '检测成功',
            'data': {
                'detections': detections,
                'statistics': {
                    'total': total,
                    'riped_count': riped_count,
                    'unriped_count': unriped_count,
                    'riped_ratio': riped_ratio
                },
                'result_image': f"data:image/jpeg;base64,{result_base64}",
                'detect_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            }
        })
        
    except Exception as e:
        print(f"检测错误: {str(e)}")
        return jsonify({
            'code': '500',
            'msg': '检测失败，请稍后重试',
            'data': None
        })


@app.route('/api/health', methods=['GET'])
def health_check():
    """健康检查接口"""
    return jsonify({
        'code': '200',
        'msg': 'API服务运行正常',
        'data': {
            'status': 'healthy',
            'model_loaded': model is not None
        }
    })


if __name__ == '__main__':
    print("=" * 50)
    print("果蔬成熟度检测 API 服务")
    print("访问地址: http://localhost:5000")
    print("检测接口: POST /api/detect")
    print("健康检查: GET /api/health")
    print("=" * 50)
    app.run(host='0.0.0.0', port=5000, debug=False)
