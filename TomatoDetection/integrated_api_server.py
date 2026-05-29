# coding:utf-8
"""
果蔬双检分析 Flask API 服务
集成果蔬成熟度检测和病虫害检测功能
"""
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
from ultralytics import YOLO
import cv2
import numpy as np
import os
import uuid
import time
import requests
from datetime import datetime
import base64

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 20 * 1024 * 1024  # 20MB 上传限制
# 允许所有来源的跨域请求
CORS(app, resources={r"/*": {"origins": "*", "methods": ["GET", "POST", "OPTIONS"], "allow_headers": ["Content-Type", "Authorization"]}})

# 配置
RIPENESS_MODEL_PATH = 'models/best.pt'  # 成熟度检测模型
DISEASE_WEIGHTS_FOLDER = 'disease_weights'  # 病虫害检测模型文件夹
UPLOAD_FOLDER = 'api_uploads'
RESULT_FOLDER = 'api_results'

# 定时清理配置
MAX_FILE_AGE_HOURS = 2  # 文件保留最大时长（小时）
CLEANUP_INTERVAL_SECONDS = 600  # 清理间隔（秒）

def cleanup_old_files():
    """定期清理超过指定时长的上传和结果文件"""
    import threading
    while True:
        time.sleep(CLEANUP_INTERVAL_SECONDS)
        try:
            now = time.time()
            for folder in [UPLOAD_FOLDER, RESULT_FOLDER]:
                if not os.path.exists(folder):
                    continue
                for fname in os.listdir(folder):
                    fpath = os.path.join(folder, fname)
                    if os.path.isfile(fpath):
                        file_age_hours = (now - os.path.getmtime(fpath)) / 3600
                        if file_age_hours > MAX_FILE_AGE_HOURS:
                            try:
                                os.remove(fpath)
                            except Exception:
                                pass
        except Exception:
            pass

# 成熟度检测类别名称
RIPENESS_CLASS_NAMES = {0: 'Riped', 1: 'UnRiped'}
RIPENESS_CH_NAMES = {0: '成熟', 1: '未成熟'}

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
        return False, f'不支持的文件类型: .{ext}，仅支持: {", ".join(sorted(ALLOWED_IMAGE_EXTENSIONS))}'
    return True, ext

# 病虫害检测标签映射
DISEASE_LABELS = {
    'rice': ['Brown_Spot（褐斑病）', 'Rice_Blast（稻瘟病）', 'Bacterial_Blight（细菌性叶枯病）'],
    'corn': ['blight（疫病）', 'common_rust（普通锈病）', 'gray_spot（灰斑病）', 'health（健康）'],
    'strawberry': ['Angular Leafspot（角斑病）', 'Anthracnose Fruit Rot（炭疽果腐病）',
                   'Blossom Blight（花枯病）', 'Gray Mold（灰霉病）', 'Leaf Spot（叶斑病）',
                   'Powdery Mildew Fruit（白粉病果）', 'Powdery Mildew Leaf（白粉病叶）'],
    'tomato': ['Early Blight（早疫病）', 'Healthy（健康）', 'Late Blight（晚疫病）',
               'Leaf Miner（潜叶病）', 'Leaf Mold（叶霉病）', 'Mosaic Virus（花叶病毒）',
               'Septoria（壳针孢属）', 'Spider Mites（蜘蛛螨）', 'Yellow Leaf Curl Virus（黄化卷叶病毒）']
}

# 病虫害防治建议库
DISEASE_ADVICE = {
    'Early Blight（早疫病）': '建议使用代森锰锌、百菌清等杀菌剂进行喷雾防治；及时摘除病叶，减少菌源；加强通风透光，降低湿度。',
    'Late Blight（晚疫病）': '建议选用甲霜灵·锰锌、烯酰吗啉等药剂防治；发现中心病株立即拔除；控制浇水，避免田间积水。',
    'Leaf Miner（潜叶病）': '建议使用阿维菌素、灭蝇胺等药剂进行防治；利用黄板诱杀成虫；清洁田园，处理残枝败叶。',
    'Leaf Mold（叶霉病）': '建议喷洒多菌灵、甲基托布津等药剂；加强温湿度管理，适时通风；选用抗病品种。',
    'Mosaic Virus（花叶病毒）': '主要通过蚜虫传播，建议使用吡虫啉防治蚜虫；发病初期喷洒病毒A、植病灵等抗病毒药剂；拔除病株，防止蔓延。',
    'Septoria（壳针孢属）': '建议使用苯醚甲环唑、代森锰锌等药剂防治；实行轮作倒茬；及时清除田间病残体。',
    'Spider Mites（蜘蛛螨）': '建议使用哒螨灵、阿维菌素等杀螨剂；清除田边杂草；保护利用天敌，如捕食螨。',
    'Yellow Leaf Curl Virus（黄化卷叶病毒）': '主要由烟粉虱传播，重点防治烟粉虱；使用防虫网阻隔；选用抗病毒品种。',
    'Brown_Spot（褐斑病）': '建议使用三环唑、多菌灵等药剂；增施磷钾肥，提高抗病力；合理灌溉，避免深水漫灌。',
    'Rice_Blast（稻瘟病）': '建议使用三环唑、富士一号等特异性药剂；选用抗病品种；科学施肥，避免氮肥过量。',
    'Bacterial_Blight（细菌性叶枯病）': '建议使用叶枯唑、农用链霉素等药剂；浅水勤灌，适时晒田；严禁带菌稻草还田。',
    'blight（疫病）': '建议使用甲霜灵、代森锰锌等药剂；加强排水，防止田间积水；实行轮作。',
    'common_rust（普通锈病）': '建议使用粉锈宁、三唑酮等药剂喷雾；清除中间寄主；选用抗病品种。',
    'gray_spot（灰斑病）': '建议使用多菌灵、甲基托布津等药剂；收获后清除病残体；实行轮作。',
    'Angular Leafspot（角斑病）': '建议使用氢氧化铜、农用链霉素等药剂；实行轮作；注意通风降湿。',
    'Anthracnose Fruit Rot（炭疽果腐病）': '建议使用咪鲜胺、炭疽福美等药剂；及时摘除病果；雨后及时排水。',
    'Blossom Blight（花枯病）': '建议在开花前喷施多菌灵、腐霉利等药剂；加强通风，降低湿度。',
    'Gray Mold（灰霉病）': '建议使用腐霉利、异菌脲等药剂；控制浇水，降低湿度；及时摘除病叶病果。',
    'Leaf Spot（叶斑病）': '建议使用代森锰锌、百菌清等药剂；加强田间管理，增强植株长势。',
    'Powdery Mildew Fruit（白粉病果）': '建议使用三唑酮、粉锈宁等药剂；加强通风透光；避免氮肥过量。',
    'Powdery Mildew Leaf（白粉病叶）': '建议使用三唑酮、粉锈宁等药剂；及时清除病叶；保持田间清洁。'
}

def generate_ai_analysis(ripeness_stats=None, disease_detections=None, crop_type='tomato'):
    """生成AI智能分析报告"""
    analysis = []
    advice = []
    
    # 1. 成熟度分析
    if ripeness_stats:
        ratio = ripeness_stats.get('riped_ratio', 0)
        total = ripeness_stats.get('total', 0)
        
        if total > 0:
            if ratio >= 80:
                analysis.append(f"当前{crop_type}作物成熟度较高（{ratio}%），已达到最佳采摘期。")
                advice.append("建议尽快安排采收，避免果实过熟腐烂。")
            elif ratio >= 50:
                analysis.append(f"当前{crop_type}作物部分成熟（{ratio}%），进入转色期。")
                advice.append("建议分批采收已成熟果实，注意保护未成熟果实。")
            else:
                analysis.append(f"当前{crop_type}作物成熟度较低（{ratio}%），处于生长发育期。")
                advice.append("建议加强水肥管理，促进果实生长和转色。")
    
    # 2. 病虫害分析
    if disease_detections is not None:
        if len(disease_detections) == 0:
            analysis.append("未发现明显的病虫害症状，作物生长状况良好。")
            advice.append("继续保持当前的田间管理措施，定期巡查。")
        else:
            diseases = {}
            for d in disease_detections:
                name = d['class_name']
                if 'Healthy' in name or 'health' in name:
                    continue
                diseases[name] = diseases.get(name, 0) + 1
            
            if not diseases:
                analysis.append("检测到的作物样本健康，未发现病害。")
                advice.append("作物生长健康，请继续保持。")
            else:
                disease_names = list(diseases.keys())
                analysis.append(f"检测到以下病虫害风险：{', '.join(disease_names)}。")
                
                for name in disease_names:
                    if name in DISEASE_ADVICE:
                        advice.append(f"【{name}】防治建议：{DISEASE_ADVICE[name]}")
                    else:
                        advice.append(f"对于【{name}】，建议咨询农业专家进行针对性防治。")
    
    return {
        'summary': "\n".join(analysis),
        'advice': "\n".join(advice)
    }

# 创建文件夹
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(RESULT_FOLDER, exist_ok=True)

# 模型缓存（线程安全）
import threading
_models = {}
_model_lock = threading.Lock()

def get_disease_model(weight_name):
    """延迟加载病虫害检测模型（线程安全）"""
    if weight_name not in _models:
        with _model_lock:
            # 双重检查，防止重复加载
            if weight_name not in _models:
                weight_path = os.path.join(DISEASE_WEIGHTS_FOLDER, weight_name)
                if os.path.exists(weight_path):
                    _models[weight_name] = YOLO(weight_path, task='detect')
                else:
                    return None
    return _models.get(weight_name)

# 加载成熟度检测模型
print("正在加载果蔬成熟度检测模型...")
try:
    ripeness_model = YOLO(RIPENESS_MODEL_PATH, task='detect')
except Exception as e:
    print(f"[警告] 成熟度检测模型加载失败: {e}")
    ripeness_model = None
print("成熟度检测模型加载完成!")

print("病虫害检测模型已准备就绪，支持按需加载...")


@app.route('/api/detect/ripeness', methods=['POST'])
def detect_ripeness():
    """
    果蔬成熟度检测接口
    接收图片文件，返回成熟度检测结果
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
        unique_name = f"ripeness_{uuid.uuid4().hex}.{file_ext}"
        
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
        
        # 进行成熟度检测
        if ripeness_model is None:
            return jsonify({'code': '500', 'msg': '成熟度检测模型未加载', 'data': None})
        results = ripeness_model(img, conf=0.5)
        
        # 解析检测结果
        detections = []
        riped_count = 0
        unriped_count = 0
        
        for result in results:
            boxes = result.boxes
            for box in boxes:
                cls_id = int(box.cls[0])
                det_conf = float(box.conf[0])
                xyxy = box.xyxy[0].tolist()

                # 统计数量
                if cls_id == 0:
                    riped_count += 1
                else:
                    unriped_count += 1

                detections.append({
                    'class_id': cls_id,
                    'class_name': RIPENESS_CLASS_NAMES.get(cls_id, 'Unknown'),
                    'class_name_ch': RIPENESS_CH_NAMES.get(cls_id, '未知'),
                    'confidence': round(det_conf * 100, 2),
                    'bbox': {
                        'x1': int(xyxy[0]),
                        'y1': int(xyxy[1]),
                        'x2': int(xyxy[2]),
                        'y2': int(xyxy[3])
                    }
                })
        
        # 绘制结果图片
        result_img = results[0].plot()
        result_name = f"ripeness_result_{unique_name}"
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
            'msg': '成熟度检测成功',
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
        print(f"成熟度检测错误: {str(e)}")
        return jsonify({
            'code': '500',
            'msg': '成熟度检测失败，请稍后重试',
            'data': None
        })


@app.route('/api/detect/disease', methods=['POST'])
def detect_disease():
    """
    病虫害检测接口
    接收图片文件和作物类型，返回病虫害检测结果
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

        # 获取作物类型
        crop_type = request.form.get('crop_type', 'tomato')  # 默认番茄
        conf = float(request.form.get('conf', 0.5))  # 默认置信度0.5

        # 验证作物类型
        if crop_type not in DISEASE_LABELS:
            return jsonify({
                'code': '400',
                'msg': f'不支持的作物类型: {crop_type}',
                'data': None
            })

        # 确定模型文件名
        model_name = f"{crop_type}_best.pt"
        unique_name = f"disease_{uuid.uuid4().hex}.{file_ext}"
        
        # 保存上传的文件
        upload_path = os.path.join(UPLOAD_FOLDER, unique_name)
        file.save(upload_path)
        
        # 加载病虫害检测模型
        disease_model = get_disease_model(model_name)
        if disease_model is None:
            return jsonify({
                'code': '400',
                'msg': f'病虫害检测模型不存在: {model_name}',
                'data': None
            })
        
        # 读取图片
        img = cv2.imdecode(np.fromfile(upload_path, dtype=np.uint8), cv2.IMREAD_COLOR)
        if img is None:
            return jsonify({
                'code': '400',
                'msg': '无法读取图片文件',
                'data': None
            })
        
        # 进行病虫害检测
        start_time = time.time()
        results = disease_model(img, conf=conf)
        elapsed_time = time.time() - start_time
        
        # 解析检测结果
        detections = []
        labels_map = DISEASE_LABELS.get(crop_type, [])
        
        for result in results:
            boxes = result.boxes
            for box in boxes:
                cls_id = int(box.cls[0])
                det_conf = float(box.conf[0])
                xyxy = box.xyxy[0].tolist()

                # 获取标签名称
                label_name = labels_map[cls_id] if cls_id < len(labels_map) else f'Unknown_{cls_id}'

                detections.append({
                    'class_id': cls_id,
                    'class_name': label_name,
                    'confidence': round(det_conf * 100, 2),
                    'bbox': {
                        'x1': int(xyxy[0]),
                        'y1': int(xyxy[1]),
                        'x2': int(xyxy[2]),
                        'y2': int(xyxy[3])
                    }
                })
        
        # 绘制结果图片
        result_img = results[0].plot()
        result_name = f"disease_result_{unique_name}"
        result_path = os.path.join(RESULT_FOLDER, result_name)
        cv2.imencode('.jpg', result_img)[1].tofile(result_path)
        
        # 将结果图片转为base64
        with open(result_path, 'rb') as f:
            result_base64 = base64.b64encode(f.read()).decode('utf-8')
        
        # 返回结果
        return jsonify({
            'code': '200',
            'msg': '病虫害检测成功',
            'data': {
                'crop_type': crop_type,
                'detections': detections,
                'statistics': {
                    'total_detections': len(detections),
                    'detection_time': round(elapsed_time, 2)
                },
                'result_image': f"data:image/jpeg;base64,{result_base64}",
                'detect_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            }
        })
        
    except Exception as e:
        print(f"病虫害检测错误: {str(e)}")
        return jsonify({
            'code': '500',
            'msg': '病虫害检测失败，请稍后重试',
            'data': None
        })


@app.route('/api/detect/both', methods=['POST'])
def detect_both():
    """
    果蔬双检分析接口 - 同时进行成熟度和病虫害检测
    接收图片文件和作物类型，返回综合检测结果
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

        # 获取作物类型
        crop_type = request.form.get('crop_type', 'tomato')
        conf = float(request.form.get('conf', 0.5))
        unique_name = f"both_{uuid.uuid4().hex}.{file_ext}"
        
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
        
        # 1. 成熟度检测
        if ripeness_model is None:
            return jsonify({'code': '500', 'msg': '成熟度检测模型未加载', 'data': None})
        ripeness_results = ripeness_model(img, conf=0.5)
        ripeness_detections = []
        riped_count = 0
        unriped_count = 0
        
        for result in ripeness_results:
            boxes = result.boxes
            for box in boxes:
                cls_id = int(box.cls[0])
                det_conf = float(box.conf[0])
                xyxy = box.xyxy[0].tolist()

                if cls_id == 0:
                    riped_count += 1
                else:
                    unriped_count += 1

                ripeness_detections.append({
                    'class_id': cls_id,
                    'class_name': RIPENESS_CLASS_NAMES.get(cls_id, 'Unknown'),
                    'class_name_ch': RIPENESS_CH_NAMES.get(cls_id, '未知'),
                    'confidence': round(det_conf * 100, 2),
                    'bbox': {
                        'x1': int(xyxy[0]),
                        'y1': int(xyxy[1]),
                        'x2': int(xyxy[2]),
                        'y2': int(xyxy[3])
                    }
                })
        
        # 2. 病虫害检测
        disease_detections = []
        if crop_type in DISEASE_LABELS:
            model_name = f"{crop_type}_best.pt"
            disease_model = get_disease_model(model_name)
            
            if disease_model:
                disease_results = disease_model(img, conf=conf)
                labels_map = DISEASE_LABELS.get(crop_type, [])
                
                for result in disease_results:
                    boxes = result.boxes
                    for box in boxes:
                        cls_id = int(box.cls[0])
                        det_conf = float(box.conf[0])
                        xyxy = box.xyxy[0].tolist()

                        label_name = labels_map[cls_id] if cls_id < len(labels_map) else f'Unknown_{cls_id}'

                        disease_detections.append({
                            'class_id': cls_id,
                            'class_name': label_name,
                            'confidence': round(det_conf * 100, 2),
                            'bbox': {
                                'x1': int(xyxy[0]),
                                'y1': int(xyxy[1]),
                                'x2': int(xyxy[2]),
                                'y2': int(xyxy[3])
                            }
                        })
        
        # 计算成熟度比例
        total = riped_count + unriped_count
        riped_ratio = round(riped_count / total * 100, 2) if total > 0 else 0
        
        # 绘制综合结果图片（使用成熟度检测结果作为基础图）
        result_img = ripeness_results[0].plot()
        result_name = f"both_result_{unique_name}"
        result_path = os.path.join(RESULT_FOLDER, result_name)
        cv2.imencode('.jpg', result_img)[1].tofile(result_path)
        
        # 将结果图片转为base64
        with open(result_path, 'rb') as f:
            result_base64 = base64.b64encode(f.read()).decode('utf-8')
        
        # 返回综合结果
        return jsonify({
            'code': '200',
            'msg': '果蔬双检分析成功',
            'data': {
                'crop_type': crop_type,
                'ripeness_analysis': {
                    'detections': ripeness_detections,
                    'statistics': {
                        'total': total,
                        'riped_count': riped_count,
                        'unriped_count': unriped_count,
                        'riped_ratio': riped_ratio
                    }
                },
                'disease_analysis': {
                    'detections': disease_detections,
                    'statistics': {
                        'total_detections': len(disease_detections)
                    }
                },
                'result_image': f"data:image/jpeg;base64,{result_base64}",
                'detect_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            }
        })
        
    except Exception as e:
        print(f"双检分析错误: {str(e)}")
        return jsonify({
            'code': '500',
            'msg': '双检分析失败，请稍后重试',
            'data': None
        })


@app.route('/api/health', methods=['GET'])
def health_check():
    """健康检查接口"""
    return jsonify({
        'code': '200',
        'msg': '果蔬双检分析API服务运行正常',
        'data': {
            'status': 'healthy',
            'ripeness_model_loaded': ripeness_model is not None,
            'disease_models_available': list(DISEASE_LABELS.keys()),
            'supported_crops': list(DISEASE_LABELS.keys())
        }
    })


@app.route('/api/models', methods=['GET'])
def get_models():
    """获取支持的模型列表"""
    return jsonify({
        'code': '200',
        'msg': '获取模型列表成功',
        'data': {
            'ripeness_model': {
                'name': '果蔬成熟度检测模型',
                'path': RIPENESS_MODEL_PATH,
                'classes': RIPENESS_CH_NAMES
            },
            'disease_models': {
                crop: {
                    'name': f'{crop}病虫害检测模型',
                    'model_file': f'{crop}_best.pt',
                    'classes': DISEASE_LABELS[crop]
                }
                for crop in DISEASE_LABELS.keys()
            }
        }
    })


if __name__ == '__main__':
    import threading
    # 启动文件清理后台线程
    cleanup_thread = threading.Thread(target=cleanup_old_files, daemon=True)
    cleanup_thread.start()

    print("=" * 60)
    print("果蔬双检分析 API 服务")
    print("访问地址: http://localhost:5000")
    print("成熟度检测: POST /api/detect/ripeness")
    print("病虫害检测: POST /api/detect/disease")
    print("双检分析:   POST /api/detect/both")
    print("健康检查:   GET /api/health")
    print("模型列表:   GET /api/models")
    print("=" * 60)
    host = os.environ.get('FLASK_HOST', '127.0.0.1')
    port = int(os.environ.get('FLASK_PORT', 5000))
    app.run(host=host, port=port, debug=False)
