# 🐰 Chibi Life v1.3 — Nguồn tham khảo & kiểm kê tính năng

Ngày khảo sát: 29/10/2026. Bản v1.3 ưu tiên **miễn phí, chạy offline, không dùng hạn mức Gemini**.

## Kết luận quan trọng

1. **Có thể** triển khai đa chương trình luyện tập bằng dữ liệu hướng dẫn, trình gợi ý offline và TTS Android. Không cần API hay máy chủ.
2. **Không thể mặc nhiên** lấy hoặc phân phối lại mô hình/giọng độc quyền CapCut trong APK. Giải pháp hỗ trợ **người dùng tự xuất file âm thanh họ được phép sử dụng** rồi nhập vào Chibi.
3. **Lưu file âm thanh** khác với **sở hữu model TTS**. Chibi phát file CapCut đã có, nhưng không thể tự đọc câu mới bằng giọng độc quyền CapCut nếu không có cấp quyền/SDK.
4. **AI thực sự chạy cục bộ** có thể phát triển bằng LiteRT-LM, nhưng cần tải model, RAM/ROM và benchmark trên thiết bị thật. Chưa có model LLM tích hợp trong APK v1.3.

## Nguồn luyện tập

| Nguồn | Tài liệu | Ứng dụng trong Chibi |
|---|---|---|
| WHO | https://www.ncbi.nlm.nih.gov/books/NBK566046/ | Vận động aerobic vừa phải 150–300 phút/tuần và tăng cường cơ từ 2 ngày/tuần ở người trưởng thành; bắt đầu từ ít và tăng dần |
| CDC | https://www.cdc.gov/physical-activity-basics/adding-adults/what-counts.html | Các bài tập sức mạnh không cần tạ (như chống đẩy), 8–12 lần/hiệp là ví dụ thường dùng |
| CDC | https://www.cdc.gov/physical-activity-basics/measuring/index.html | Talk test: vận động vừa sức vẫn có thể nói, không hát; không dùng nhịp tim để ép mục tiêu trong ứng dụng |
| NHS | https://www.nhs.uk/live-well/exercise/strength-exercises/ | Cách tập đứng lên–ngồi xuống, mini squat và chống đẩy tường; ghế dùng phải chắc chắn |
| WHO | https://www.who.int/news-room/fact-sheets/detail/healthy-diet | Bữa ăn đa dạng, rau quả, ngũ cốc/đậu, hạn chế muối và đường tự do |

**Giới hạn sức khỏe:** đây là giáo trình tham khảo cho người trưởng thành nói chung, không thay thế chẩn đoán, tập vật lý trị liệu hoặc hướng dẫn cho người có bệnh nền. Không kê calo cụ thể, không khuyến khích nhịn ăn khắc nghiệt, không hứa giảm mỡ cục bộ. Gợi ý món ăn không được chứng nhận an toàn dị ứng; kiểm tra nguyên liệu thực tế.

## 12 chương trình đã được thêm

- 🌱 Bắt đầu nhẹ nhàng
- 💪 Toàn thân không dụng cụ
- 🚶 Cardio không bật nhảy
- 🦵 Chân và mông
- 🏋️ Thân trên
- 🔥 Core và ổn định cơ bụng
- 🧘 Linh hoạt và giãn cơ
- 💻 Nghỉ vận động tại bàn
- 🌿 Thăng bằng cơ bản có điểm tựa
- ⚡ 10 phút cho người bận rộn
- 🚀 Thử thách nâng cao
- 🌙 Ngày phục hồi tích cực

**Các lựa chọn trong UI:** lọc bộ môn, mức kinh nghiệm, thời gian, mục tiêu hôm nay, năng lượng hiện tại. Huấn luyện bằng từng bước trên điện thoại có TTS tiếng Việt, nút bắt đầu/tạm dừng/bỏ qua, tiến trình, nhật ký buổi tập. Lịch tuần chỉ được thêm sau khi người dùng chấp thuận.

## Nguồn giọng Việt

| Nguồn | URL | Tình trạng sử dụng |
|---|---|---|
| CapCut chính thức | https://www.capcut.com/vi-vn/tools/text-to-speech | Tạo tiếng Việt và xuất âm thanh, tùy tính năng/tài khoản. Chibi chỉ nhận file người dùng xuất |
| CapCut Terms | https://www.capcut.com/clause/terms-of-service | Phải xem phạm vi cấp phép; không phân phối lại kho giọng độc quyền trong APK |
| CapCut Materials License | https://www.capcut.com/clause/material-license-agreement | Âm thanh và nhạc thuộc các phạm vi quyền riêng; không mặc nhiên có quyền cho mọi mục đích |
| Android TextToSpeech | https://developer.android.com/reference/android/speech/tts/TextToSpeech | Các giọng do engine Android công bố, preview, chọn, pitch và rate |
| Android MediaRecorder | https://developer.android.com/media/platform/mediarecorder | Tự ghi âm tiếng nói, người dùng phải cấp quyền microphone |
| Android Storage Access Framework | https://developer.android.com/training/data-storage/shared/documents-files | Chọn tệp bằng picker, không yêu cầu đọc toàn bộ thư viện |
| Android media formats | https://developer.android.com/media/platform/supported-formats | Định dạng âm thanh phổ biến MP3/M4A/WAV/OGG/FLAC |
| HayaiTTS (mã nguồn mở) | https://github.com/HayaiApp/HayaiTTS | Engine chạy offline; GPL-3.0 và giấy phép từng voice khác nhau. Không đóng gói lại mặc định |

### Quy trình giọng CapCut

1. Mở CapCut, tạo văn bản lời báo thức bằng tiếng Việt và chọn giọng phù hợp.
2. Dùng tùy chọn xuất âm thanh/tải xuống được cung cấp bởi phiên bản CapCut đang dùng (MP3, M4A hoặc WAV nếu hỗ trợ). Kiểm tra quyền sử dụng.
3. Trong Chibi v1.3 → **Thư viện giọng CapCut & ghi âm** → **Nhập âm thanh**.
4. Đặt tên → **Nghe thử** → **Chọn** → báo thức phát lại file đã lưu trên máy, không cần Gemini.
5. Chibi cũng cho phép tự ghi âm bằng micro, hoặc chuyển lại sang giọng Android TTS.

**Giới hạn:** file nhập có độ dài tối đa 5 phút, kích thước tối đa 20 MB, tối đa 15 bản ghi. Chibi không tự tổng hợp câu mới bằng mô hình CapCut.

## Định hướng AI local thế hệ sau

- Google LiteRT-LM: https://developers.google.com/edge/litert-lm/overview
- Google AI Edge Gallery: https://github.com/google-ai-edge/gallery
- Gemma trên mobile: https://ai.google.dev/gemma/docs/integrations/mobile
- HayaiTTS: https://github.com/HayaiApp/HayaiTTS

Để thật sự tích hợp mô hình sinh ngôn ngữ offline cần hỗ trợ Android Native, tải model có cấp phép, quản lý dung lượng/thiết bị và đánh giá trên Android 16. **Chưa triển khai trong v1.3.** Trong v1.3, “Chibi gợi ý” hoạt động bằng quy tắc và dữ liệu nội bộ, không phát sinh hóa đơn API.

## Kiểm thử đề xuất

1. Chạy từng loại chương trình luyện tập: mở mô tả, bắt đầu, tạm dừng, bước tiếp, kết thúc, lưu nhật ký.
2. Tạo lịch tuần và kiểm tra ngày, giờ, nhắc sau khi khởi động lại máy.
3. Thử 3 phong cách dinh dưỡng, lựa chọn tránh sữa/trứng/hải sản/đậu phộng, nhật ký 7 ngày.
4. Nhập MP3, M4A, WAV 10–20 giây; nghe thử, đặt báo thức, tắt mạng, khóa màn hình và kiểm tra âm thanh.
5. Chọn lại giọng Android TTS và xác nhận nhập âm thanh không còn ghi đè nó.
6. Ghi âm 5–10 giây, chọn làm báo thức, xóa file và kiểm tra fallback.
7. Kiểm tra permission RECORD_AUDIO, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, full-screen trên Android 16.

**Build thành công chưa chứng minh kiểm thử thực tế trên thiết bị.**
