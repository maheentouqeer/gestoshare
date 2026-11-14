import cv2, json, numpy as np, onnxruntime as ort, mediapipe as mp
from collections import deque
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision

landmarker = mp_vision.HandLandmarker.create_from_options(mp_vision.HandLandmarkerOptions(
    base_options=mp_python.BaseOptions(model_asset_path="hand_landmarker.task"),
    running_mode=mp_vision.RunningMode.IMAGE, num_hands=1, min_hand_detection_confidence=0.5))
sess = ort.InferenceSession("airshare_gesture.onnx", providers=["CPUExecutionProvider"])
meta = json.load(open("labels.json")); CLASSES, ACTIONS = meta["class_order"], meta["actions"]
THRESH, VOTES = 0.8, deque(maxlen=5)

cap = cv2.VideoCapture(0)
while True:
    ok, frame = cap.read()
    if not ok: break
    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB); H, W = rgb.shape[:2]
    res = landmarker.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.ascontiguousarray(rgb)))
    label, conf = "no hand", 0.0
    if res.hand_landmarks:
        pts = np.array([[p.x*W, p.y*H, p.z*W] for p in res.hand_landmarks[0]], dtype=np.float32)
        pts -= pts[0]; pts /= (np.linalg.norm(pts[9]) + 1e-6)
        probs = sess.run(None, {"landmarks": pts.reshape(1, 63).astype(np.float32)})[1][0]
        i = int(probs.argmax()); label, conf = CLASSES[i], float(probs[i])
    VOTES.append(label if conf >= THRESH else "OTHER")
    stable = max(set(VOTES), key=VOTES.count)
    action = ACTIONS.get(stable, "") if VOTES.count(stable) >= 4 and stable != "OTHER" else ""
    cv2.putText(frame, f"{label} {conf:.2f}", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
    cv2.putText(frame, f"ACTION: {action}", (10, 70), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 200, 255), 2)
    cv2.imshow("GestoShare live test", frame)
    if cv2.waitKey(1) & 0xFF == ord("q"): break
cap.release(); cv2.destroyAllWindows()