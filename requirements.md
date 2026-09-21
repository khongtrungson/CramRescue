

# CramRescue 48h — Trợ Lý Sinh Tồn Phòng Thi

## 0. Document Purpose

Tài liệu Đặc tả Yêu cầu Sản phẩm (PRD) này xác định phạm vi chức năng, luồng trải nghiệm người dùng, các yêu cầu phi chức năng (NFR) và ràng buộc kỹ thuật cho ứng dụng di động **CramRescue 48h** phiên bản v1 (MVP).

---

## 1. Vision

**CramRescue 48h** là ứng dụng Android Native (Kotlin/Jetpack Compose) được thiết kế chuyên biệt cho tình huống khẩn cấp: học sinh và sinh viên cần vượt qua kỳ thi sống còn trong vòng 24–48 giờ trước giờ G. 

Khác với các công cụ học tập thông thường hướng tới tích lũy dài hạn qua cả học kỳ (Anki, Quizlet) hoặc các công cụ sinh tóm tắt thụ động gây ngợp (NotebookLM, ChatPDF), CramRescue 48h đóng vai trò như một **"Buồng lái sinh tồn" (Survival Cockpit)**:
1. Áp dụng triệt để nguyên lý **Pareto 80/20** để lọc ra 20% kiến thức trọng tâm quyết định 80% điểm số an toàn.
2. Phân loại nội dung theo cơ chế phòng cấp cứu **ER Triage (Code Red / Yellow / Green)**, thẳng tay loại bỏ kiến thức râu ria.
3. Chuyển hóa tài liệu in tiêu chuẩn (ảnh chụp, PDF, Word) thành các **Micro-Card Active Recall** tuân thủ nghiêm ngặt **Khung 3 dòng (3-Bullet Budget)** với tính năng **Đối chiếu nguồn (Source-Grounding)** 1 chạm về ảnh gốc để dập tắt nỗi sợ ảo giác AI.

Sản phẩm hoạt động theo mô hình **BYOK (Bring Your Own Key)** hoàn toàn trên thiết bị (On-device OCR kết hợp Google Gemini API client-side), mang lại tốc độ tức thì, chi phí vận hành 0 đồng và bảo mật dữ liệu tuyệt đối.

---

## 2. Target User

### 2.1 Jobs To Be Done (JTBD)
- **Functional (Chức năng):** 
  - Khi chỉ còn 48h hoặc 24h trước môn thi mà chưa học gì hoặc tài liệu quá dày, tôi muốn trích xuất ngay những nội dung chắc chắn sẽ ra đề để học trọng tâm, không bị điểm liệt.
  - Tôi muốn kiểm tra phản xạ kiến thức chủ động (Active Recall) trong các phiên học ngắn 15 phút để biết chắc mình đã nhớ được công thức / khái niệm cốt lõi.
- **Emotional (Cảm xúc):** 
  - Giảm bớt sự tê liệt vì hoảng loạn (Panic Paralysis), lấy lại cảm giác kiểm soát thời gian thông qua đồng hồ đếm ngược và chỉ số % qua môn tăng dần theo từng chặng.
  - Tự tin rằng nội dung AI tóm tắt là chính xác 100% dựa vào việc đối chiếu trực tiếp với trang tài liệu gốc.
- **Contextual (Ngữ cảnh):** 
  - Thao tác nhanh trên điện thoại Android, có thể học tiếp offline trên xe bus, thư viện hoặc ngay trước cổng phòng thi.

### 2.2 Non-Users (v1)
- Người học ngoại ngữ dài hạn muốn tích lũy từ vựng theo chu kỳ nhiều tháng/năm.
- Người muốn học sâu để đạt điểm tuyệt đối (9.5 - 10.0) cần đọc toàn bộ giáo trình chi tiết.
- Người dùng chỉ có tài liệu viết tay mờ nhòe hoặc ghi chú vẽ tay tự do [ASSUMPTION: v1 chỉ tập trung xử lý tài liệu in tiêu chuẩn].

### 2.3 Key User Journeys (UJ)

#### UJ-1: Minh tạo chiến dịch sinh tồn 48h cho môn Kinh Tế Vi Mô
- **Persona + context:** Minh, sinh viên năm 2 Đại học Kinh tế, còn đúng 36 tiếng trước bài thi kết thúc học phần Kinh Tế Vi Mô. Cậu đang cầm tệp đề cương ôn tập PDF 25 trang do giảng viên gửi nhưng chưa đọc chữ nào.
- **Entry state:** Minh cài app lần đầu, mở app và nhập Google Gemini API Key cá nhân trong 30 giây.
- **Path:** 
  1. Minh nhấn **"Tạo chiến dịch sinh tồn"**, đặt tên "Kinh Tế Vi Mô", chọn mốc giờ thi là 8:00 sáng ngày mốt.
  2. Chọn Nhóm môn học: **Xã hội / Lý thuyết**.
  3. Minh chọn tải lên tệp PDF đề cương (chọn phạm vi 18 trang trọng tâm).
  4. App quét OCR On-device và gửi qua Gemini API để phân loại ER Triage. Trong 25 giây, màn hình hiển thị kết quả: 14 thẻ **Code Red** (Buộc phải thuộc), 18 thẻ **Code Yellow** (Kéo điểm an toàn), loại bỏ 45% nội dung râu ria (**Code Green**).
- **Climax:** Buồng lái sinh tồn hiển thị: Đồng hồ đếm ngược `35:59:12`, Thước đo xác suất qua môn xuất phát ở mức `10%`, danh sách 3 nhiệm vụ cấp cứu đầu tiên (chặng 15 phút) sẵn sàng. Minh thở phào vì nhìn thấy đường sống rõ ràng thay vì tệp PDF vô tận.
- **Resolution:** Minh bấm bắt đầu chặng 1, làm quen với 5 thẻ Code Red đầu tiên có ẩn từ khóa.
- **Edge case:** Nếu tệp PDF vượt quá 20 trang, app cảnh báo và yêu cầu Minh chọn lọc 20 trang quan trọng nhất trước khi tiến hành bóc tách.

#### UJ-2: Linh kiểm tra phản xạ Active Recall và đối chiếu nguồn gốc
- **Persona + context:** Linh, sinh viên Y-Dược, đang ôn thi Dược Lý khẩn cấp trước giờ thi 12 tiếng.
- **Entry state:** Đang trong chặng ôn tập thứ 3 trên xe buýt.
- **Path:** 
  1. Linh mở ứng dụng, màn hình vào thẳng Buồng lái hiển thị 8 thẻ cần ôn lại.
  2. Thẻ xuất hiện với câu hỏi định nghĩa và chỗ trống cần điền (Cloze Deletion).
  3. Linh tự nhẩm đáp án trong đầu rồi chạm nhẹ để lật thẻ xem kết quả.
  4. Cảm thấy nghi ngờ một tác dụng phụ của thuốc có đúng với bài giảng không, Linh bấm biểu tượng kính lúp **"Đối chiếu nguồn"**.
- **Climax:** Ngay lập tức, hình ảnh trang slide bài giảng gốc hiển thị toàn màn hình, đóng khung chữ nhật màu vàng (Bounding Box) ngay dòng chữ giảng viên viết về tác dụng phụ đó. Linh xác thực thông tin 100% trong vòng 2 giây mà không cần lật lại file gốc.
- **Resolution:** Linh đánh dấu "Đã thuộc", thước đo Xác suất an toàn tăng từ `58%` lên `64%`.
- **Edge case:** Nếu mất mạng Internet trên xe buýt, ứng dụng vẫn hoạt động trơn tru vì toàn bộ thẻ và ảnh cắt đã được lưu offline trong Room DB.

#### UJ-3: Tuấn thực hiện lượt quét cấp tốc (Emergency Warm-up) trước giờ vào phòng thi
- **Persona + context:** Tuấn, sinh viên Bách Khoa, còn 45 phút nữa là đến giờ giám thị gọi vào phòng thi môn Giải tích 2.
- **Entry state:** Điện thoại ở chế độ máy bay để tránh thông báo làm phân tâm.
- **Path:** 
  1. Tuấn mở app, chuyển sang chế độ **"Emergency Speed-run"**.
  2. Hệ thống lọc riêng 10 công thức thuộc nhóm **Code Red** mà Tuấn từng trả lời sai trong 24h qua.
  3. Tuấn vuốt lướt nhanh dạng quiz trắc nghiệm phản xạ 10 giây/câu.
- **Climax:** Hoàn thành 10/10 công thức trong 7 phút, đồng hồ báo: "Trạng thái sẵn sàng: Đã khóa kiến thức cốt lõi phòng thi".
- **Resolution:** Tuấn cất điện thoại vào balo và tự tin bước vào phòng thi.

---

## 3. Glossary

Mọi thuật ngữ miền (Domain Terms) trong tài liệu này được định nghĩa chuẩn xác và phải được sử dụng nguyên văn trong toàn bộ tài liệu kỹ thuật, thiết kế và mã nguồn:

- **Chiến dịch sinh tồn (Survival Campaign):** Phiên ôn thi mục tiêu duy nhất gắn liền với một môn học và một thời điểm thi cụ thể (Countdown Target $T_0$).
- **Buồng lái sinh tồn (Survival Cockpit):** Màn hình điều khiển trung tâm hiển thị thời gian đếm ngược thực tế, thước đo Xác suất an toàn và các nhiệm vụ ôn tập vi mô kế tiếp.
- **Xác suất an toàn (Pass Probability):** Chỉ số phần trăm ước lượng năng lực qua môn (từ 0% đến 100%), tính toán dựa trên trọng số độ thuần thục của các thẻ Code Red (70%) và Code Yellow (30%).
- **Thẻ vi mô (Micro-Card):** Đơn vị kiến thức cô đọng tối đa, bao gồm tiêu đề khái niệm, nội dung tóm tắt theo Khung 3 dòng và các từ khóa ẩn phục vụ Active Recall.
- **Phân loại ER (ER Triage):** Quy tắc phân cấp nội dung kiến thức theo mức độ cấp cứu phòng thi:
  - **Code Red (Báo động đỏ):** Kiến thức trọng tâm sống còn, công thức buộc phải thuộc để tránh điểm liệt (chiếm ~20% dung lượng nhưng quyết định qua môn).
  - **Code Yellow (Báo động vàng):** Kiến thức quan trọng giúp nâng điểm lên ngưỡng an toàn (5.0 – 6.5).
  - **Code Green (Báo động xanh):** Kiến thức râu ria, nâng cao — loại bỏ hoàn toàn trong phạm vi 48h.
- **Khung 3 dòng (3-Bullet Budget):** Ràng buộc định dạng bắt buộc cho mỗi Micro-Card: Tối đa 3 gạch đầu dòng, mỗi dòng không quá 20 từ.
- **Ẩn từ khóa (Cloze Deletion):** Kỹ thuật che phủ từ khóa, công thức hoặc điều kiện tiên quyết trên thẻ, buộc người học phải tư duy phản xạ trước khi hiển thị đáp án.
- **Đối chiếu nguồn (Source-Grounding):** Khả năng nhảy trực tiếp 1 chạm từ một Micro-Card về vị trí Bounding Box (khung tọa độ) trên trang tài liệu gốc đã scan.
- **Nhóm môn học (Subject Archetype):** 4 mẫu hình cấu trúc môn học: *Tự nhiên/Tính toán*, *Xã hội/Luật/Lý luận*, *Lập trình/Công nghệ*, và *Ngoại ngữ*.
- **BYOK (Bring Your Own Key):** Cơ chế người dùng tự cung cấp Google Gemini API Key cá nhân để app kết nối trực tiếp, không qua máy chủ trung gian.

---

## 4. Features

### 4.1 Khởi tạo Chiến dịch & Nạp Tài liệu Đa Định dạng (Campaign & Multi-Format Ingestion)
**Description:** Cho phép người học thiết lập nhanh một Chiến dịch sinh tồn với mốc thời gian thi, lựa chọn Nhóm môn học và nạp tài liệu từ Camera, Thư viện ảnh, PDF hoặc file Word (DOC/DOCX). Hệ thống giới hạn tối đa 20 trang tóm tắt/đề cương cho một lần quét để đảm bảo tính tối ưu của nguyên lý Pareto và tránh vượt ngưỡng xử lý token. Realizes UJ-1.

**Functional Requirements:**

#### FR-1: Thiết lập thông tin Chiến dịch sinh tồn
Học sinh có thể tạo một chiến dịch mới bằng cách nhập tên môn học, chọn thời điểm thi ($T_0$) và chọn 1 trong 4 Nhóm môn học. Realizes UJ-1.
**Consequences (testable):**
- Hệ thống từ chối mốc thời gian thi trong quá khứ hoặc quá xa (hơn 7 ngày) kèm thông báo lỗi phù hợp [ASSUMPTION: Giới hạn tối đa 7 ngày để giữ đúng bản chất ôn thi cấp tốc].
- Sau khi tạo, một bản ghi Campaign được lưu vào Room DB với trạng thái `active`.

#### FR-2: Nhập tài liệu qua Camera và Thư viện ảnh
Học sinh có thể chụp ảnh trực tiếp các trang đề cương in hoặc chọn nhiều ảnh từ Bộ sưu tập của thiết bị (tối đa 20 ảnh/trang). Realizes UJ-1.
**Consequences (testable):**
- Hệ thống từ chối nếu người dùng chọn quá 20 ảnh và hiển thị thông báo yêu cầu lọc lại trang quan trọng.
- Ứng dụng tự động điều chỉnh hướng ảnh (rotation) và nén nhẹ để tối ưu bộ nhớ trước khi OCR.

#### FR-3: Nhập tài liệu qua Tệp PDF và DOC/DOCX
Học sinh có thể chọn 1 tệp PDF hoặc Word từ bộ nhớ máy. Realizes UJ-1.
**Consequences (testable):**
- Với file PDF có trên 20 trang, ứng dụng hiển thị giao diện chọn phạm vi trang (ví dụ: trang 1-20 hoặc các trang lẻ/chẵn) và chỉ render tối đa 20 trang đã chọn.
- Với file DOC/DOCX, hệ thống trích xuất text có định dạng phân đoạn đoạn văn và giới hạn tối đa 10.000 từ [ASSUMPTION: Tương đương dung lượng khoảng 20 trang in].

#### FR-4: Nhận diện ký tự On-device (Google ML Kit OCR)
Hệ thống tự động chạy nhận diện văn bản trên thiết bị cho từng trang ảnh/PDF mà không cần kết nối mạng. Realizes UJ-1, UJ-2.
**Consequences (testable):**
- Trích xuất toàn bộ Text Blocks cùng tọa độ Bounding Box của từng block trên ảnh gốc.
- Nếu chất lượng ảnh quá mờ hoặc không nhận diện được văn bản (dưới 50 ký tự/trang), hệ thống hiển thị cảnh báo trang lỗi và gợi ý chụp lại.

**Feature-specific NFRs:**
- Tốc độ OCR trên thiết bị: Không quá 1.5 giây/trang trên các thiết bị Android tầm trung (Snapdragon 680 trở lên).

---

### 4.2 Phân tích Pareto 80/20 & Chắt lọc Tri thức theo Phân loại ER
**Description:** Sử dụng mô hình Gemini API với prompt chuyên biệt theo từng Nhóm môn học để phân tích văn bản đã OCR, phân loại nội dung thành Code Red / Yellow / Green và trích xuất cấu trúc Micro-Card tuân thủ Khung 3 dòng. Realizes UJ-1.

**Functional Requirements:**

#### FR-5: Phân cấp nội dung theo ER Triage
Hệ thống gửi văn bản đã trích xuất lên Gemini API kèm chỉ thị phân loại nghiêm ngặt: nhận diện và gán nhãn từng đơn vị kiến thức thành Code Red, Code Yellow hoặc Code Green. Realizes UJ-1.
**Consequences (testable):**
- Phản hồi từ Gemini API được ép cấu trúc JSON chuẩn xác (Structured Output).
- Tất cả nội dung thuộc nhóm Code Green bị loại bỏ khỏi danh sách ôn tập và chỉ lưu trữ thống kê tóm tắt (ví dụ: "Đã lược bỏ 12 khái niệm râu ria/nâng cao").

#### FR-6: Thực thi Khung 3 dòng (3-Bullet Budget)
Mỗi Micro-Card được tạo ra phải chứa tối đa 3 gạch đầu dòng, mỗi gạch đầu dòng không quá 20 từ. Realizes UJ-1, UJ-2.
**Consequences (testable):**
- Hệ thống thực hiện kiểm tra biểu thức (validation rule) cục bộ trên client; nếu bất kỳ thẻ nào vượt quá 3 dòng hoặc quá 20 từ/dòng, client tự động kích hoạt hàm cắt tỉa hoặc định dạng lại để không làm vỡ giao diện buồng lái.

#### FR-7: Tùy biến prompt theo Nhóm môn học (Subject Archetype)
Hệ thống tự động điều chỉnh cấu trúc trích xuất dựa trên archetype đã chọn:
- *Tự nhiên/Tính toán:* Bắt buộc trích xuất biến số, đơn vị, công thức và điều kiện áp dụng.
- *Xã hội/Lý thuyết:* Bắt buộc trích xuất định nghĩa chuẩn, từ khóa so sánh và khung 3 bước lập luận.
- *Lập trình/CNTT:* Bắt buộc trích xuất đoạn mã mẫu tối giản (Code snippet) và bẫy lỗi cú pháp.
- *Ngoại ngữ:* Bắt buộc trích xuất ngữ cảnh áp dụng và cụm từ cố định (collocation). Realizes UJ-1.
**Consequences (testable):**
- Payload gửi lên Gemini API có chứa đúng System Prompt template tương ứng với Nhóm môn học đã chọn.

---

### 4.3 Buồng lái Sinh tồn 48h & Thước đo Xác suất An toàn
**Description:** Cung cấp giao diện trực quan mang không khí phòng điều khiển khẩn cấp: Đồng hồ đếm ngược từng giây đến giờ thi, thanh Xác suất an toàn tính toán động, và điều phối các chặng học vi mô 15 phút (micro-missions). Realizes UJ-1, UJ-3.

**Functional Requirements:**

#### FR-8: Đồng hồ đếm ngược thời gian thực (Survival Countdown)
Buồng lái hiển thị đồng hồ đếm ngược định dạng `HH:MM:SS` đến thời điểm thi $T_0$. Realizes UJ-1, UJ-3.
**Consequences (testable):**
- Đồng hồ cập nhật mỗi giây một lần thông qua Kotlin StateFlow mà không gây lag giao diện Compose.
- Khi thời gian còn dưới 6 tiếng, giao diện chuyển sang trạng thái cảnh báo đặc biệt (Emergency High-alert).

#### FR-9: Tính toán Thước đo Xác suất An toàn (Pass Probability Indicator)
Hệ thống tính toán động tỷ lệ % Xác suất an toàn dựa trên công thức trọng số:
$$\text{Pass Probability} = (0.70 \times \text{Tỷ lệ hoàn thành Red}) + (0.30 \times \text{Tỷ lệ hoàn thành Yellow})$$
Realizes UJ-1, UJ-2.
**Consequences (testable):**
- Tỷ lệ hiển thị là số nguyên từ 0% đến 100%.
- Khi người học hoàn thành hoặc đánh dấu thuộc một thẻ Code Red, chỉ số tăng lên lập tức với hiệu ứng phản hồi trực quan (haptic feedback nhẹ và thanh tiến trình chạy mượt).

#### FR-10: Phân bổ chặng học vi mô 15 phút (Micro-missions)
Hệ thống tự động nhóm các Micro-Card thành các gói nhiệm vụ ngắn (mỗi chặng gồm 5-7 thẻ), thiết kế để hoàn thành trong 10–15 phút. Realizes UJ-1.
**Consequences (testable):**
- Người học có thể tạm dừng và tiếp tục bất cứ lúc nào mà không bị mất tiến độ chặng hiện tại.

---

### 4.4 Thẻ Ghi nhớ Chủ động & Quiz Phản xạ Sống còn (Active Recall & Survival Quiz)
**Description:** Cơ chế hiển thị thẻ học tương tác với từ khóa bị che giấu (Cloze Deletion) và bộ câu hỏi trắc nghiệm/phản xạ nhanh giúp củng cố kiến thức trước giờ thi. Realizes UJ-2, UJ-3.

**Functional Requirements:**

#### FR-11: Hiển thị Thẻ vi mô với Ẩn từ khóa (Cloze Deletion)
Mỗi Micro-Card che giấu 1-2 từ khóa cốt lõi bằng ô màu tương phản; người học phải chạm vào ô để mở đáp án. Realizes UJ-2.
**Consequences (testable):**
- Trạng thái ban đầu của từ khóa luôn bị ẩn.
- Chạm vào từ khóa sẽ kích hoạt hiệu ứng mở đáp án (reveal).

#### FR-12: Tự đánh giá mức độ ghi nhớ (Self-assessment Grading)
Sau khi mở đáp án, người học tự chọn 1 trong 3 mức đánh giá: "Chưa nhớ" (Redo soon), "Tạm ổn" (Review in 2h), hoặc "Đã thuộc" (Mastered). Realizes UJ-2.
**Consequences (testable):**
- Lựa chọn này cập nhật trường `masteryLevel` trong Room DB và xếp lịch lặp lại thẻ theo thuật toán nén thời gian (Survival Repetition).

#### FR-13: Chế độ ôn tập cấp tốc trước giờ thi (Emergency Speed-run)
Chế độ cho phép người học rà soát nhanh toàn bộ thẻ Code Red chưa đạt trạng thái "Đã thuộc" trong vòng 10 phút. Realizes UJ-3.
**Consequences (testable):**
- Bỏ qua các thẻ Code Yellow và các thẻ đã thuộc, tập trung 100% thời gian cho các lỗ hổng kiến thức nguy cấp.

---

### 4.5 Đối chiếu Nguồn Chống Ảo giác (Source-Grounding Verification)
**Description:** Mỗi Micro-Card lưu trữ liên kết trỏ về số trang tài liệu gốc và tọa độ Bounding Box của nội dung nguồn. Người dùng có thể nhấn nút "Đối chiếu nguồn" để xem ngay vị trí thực tế trên trang tài liệu ban đầu. Realizes UJ-2.

**Functional Requirements:**

#### FR-14: Liên kết Tọa độ Nguồn (Source Grounding Linkage)
Khi Gemini API trích xuất một Micro-Card, nó phải trả về chỉ số đoạn văn bản gốc hoặc số trang tương ứng. Hệ thống client tự động mapping với Bounding Box thu được từ ML Kit OCR. Realizes UJ-2.
**Consequences (testable):**
- Trường `sourcePageNumber` và `sourceBoundingBox` không được rỗng đối với tài liệu dạng ảnh hoặc PDF.
- Đối với tài liệu Word (DOC/DOCX), hệ thống hiển thị đoạn văn bản trích dẫn nguồn (Text Snippet).

#### FR-15: Giao diện Xem nguồn tức thì (Grounding Viewer Overlay)
Người học chạm vào biểu tượng "Đối chiếu nguồn" trên bất kỳ thẻ nào để mở lớp phủ xem trước trang tài liệu gốc với khung highlight chữ nhật bao quanh vùng kiến thức. Realizes UJ-2.
**Consequences (testable):**
- Thời gian mở overlay và hiển thị vùng highlight dưới 300ms.
- Cho phép phóng to (pinch-to-zoom) để đọc chi tiết ngữ cảnh bài giảng gốc.

---

### 4.6 Quản lý Thiết bị & Cấu hình BYOK Gemini API
**Description:** Cho phép người dùng lưu trữ an toàn Google Gemini API Key cá nhân trên thiết bị và quản lý dữ liệu ôn tập hoàn toàn cục bộ (Offline-first với Room DB).

**Functional Requirements:**

#### FR-16: Lưu trữ và Kiểm tra tính hợp lệ của Gemini API Key
Học sinh có thể nhập API Key trong màn hình Cài đặt. Ứng dụng cung cấp nút "Kiểm tra kết nối" để xác thực key với Google API.
**Consequences (testable):**
- API Key được mã hóa và lưu trữ an toàn bằng `EncryptedSharedPreferences`.
- Nếu key không hợp lệ hoặc hết hạn ngạch (Quota 429), ứng dụng hiển thị thông báo lỗi rõ ràng và cung cấp đường dẫn hướng dẫn lấy key miễn phí từ Google AI Studio.

#### FR-17: Hoạt động Offline hoàn toàn sau khi bóc tách (Offline Storage)
Toàn bộ dữ liệu Chiến dịch, thẻ Micro-Card, tiến độ học và ảnh tài liệu đã lưu được truy xuất trực tiếp từ Room DB và bộ nhớ ứng dụng mà không cần kết nối mạng. Realizes UJ-2, UJ-3.
**Consequences (testable):**
- Ứng dụng hoạt động đầy đủ chức năng học tập, đếm ngược, ôn thẻ và xem nguồn khi thiết bị ở chế độ Airplane Mode (sau khi quá trình tạo thẻ ban đầu đã hoàn tất).

---

## 5. Cross-Cutting Non-Functional Requirements (NFRs)

### 5.1 Hiệu năng (Performance)
- **Khởi động ứng dụng (Cold Start):** Dưới 1.2 giây trên thiết bị Android tiêu chuẩn (Android 10+, RAM 4GB).
- **Phản hồi giao diện (UI Smoothness):** Duy trì 60 fps ổn định trên Jetpack Compose, đặc biệt khi vuốt lật thẻ và cuộn buồng lái.
- **Thời gian xử lý nén 80/20:** Toàn bộ quá trình từ khi OCR xong 20 trang đến khi nhận toàn bộ thẻ từ Gemini API không quá 45 giây (tùy thuộc vào tốc độ mạng).

### 5.2 Khả năng phục hồi & Độ tin cậy (Reliability)
- **Tự động lưu trạng thái (Auto-save):** Mọi thao tác đánh giá thẻ, hoàn thành chặng học được ghi xuống Room DB ngay lập tức (Zero-data-loss khi app bị kill do thiếu RAM).
- **Xử lý lỗi mạng:** Nếu mất mạng trong lúc Gemini API đang phân tích, hệ thống lưu lại văn bản OCR cục bộ và cho phép "Thử lại phân tích" mà không cần quét lại tài liệu.

### 5.3 Bảo mật & Quyền riêng tư (Security & Privacy)
- **Không thu thập dữ liệu (Zero-telemetry v1):** Tài liệu học tập, ảnh đề thi và API Key của người dùng không bao giờ được gửi về bất kỳ máy chủ nào ngoài endpoint chính thức của Google Gemini API (`generativelanguage.googleapis.com`).
- **Lưu trữ bảo mật:** Mã hóa thông tin cấu hình nhạy cảm bằng Android Keystore System.

### 5.4 Khả năng tương thích thiết bị (Device Compatibility)
- Hỗ trợ hệ điều hành: Tối thiểu Android 8.0 (API level 26) trở lên.
- Tối ưu hiển thị: Thích ứng tốt từ màn hình điện thoại nhỏ (5.5 inch) đến màn hình lớn và chế độ Dark Mode (chế độ nền tối mặc định để bảo vệ mắt học sinh thức đêm ôn thi).

---

## 6. Constraints & Guardrails

1. **Ràng buộc chống ảo giác (Anti-Hallucination Guardrail):**
   - Prompt Gemini API bắt buộc kèm quy tắc: "Chỉ được trích xuất dựa trên thông tin có trong văn bản được cung cấp. Tuyệt đối không tự suy diễn hoặc bổ sung công thức ngoài đề cương."
   - Mỗi thẻ bắt buộc phải gắn với ít nhất một `sourceTextSnippet` từ tài liệu gốc.
2. **Ràng buộc ngân sách độ dài (Strict 3-Bullet Budget):**
   - Giới hạn cứng trong code client: Nếu AI trả về dòng thứ 4, client tự động ẩn dòng đó và gán vào phần "Ghi chú mở rộng" (Notes).
3. **Giới hạn số trang xử lý (Input Page Ceiling):**
   - Giới hạn cứng tối đa 20 trang tài liệu cho mỗi chiến dịch ôn thi nhằm tránh vượt quá giới hạn token đầu ra của Gemini API và giữ đúng định vị "sống còn 80/20".

---

## 7. Aesthetic & Tone

- **Phong cách thị giác (Visual Aesthetic):** 
  - Giao diện mang hơi hướng **Buồng lái khẩn cấp (Emergency Tactical Cockpit)**.
  - Tông màu chủ đạo: Nền tối (Dark Slate/OLED Black), điểm nhấn màu đỏ báo động (Alert Red - Code Red), vàng cảnh báo (Caution Amber - Code Yellow), và xanh an toàn (Survival Green - Pass Indicator).
  - Độ tương phản cao, phông chữ sans-serif đậm rõ ràng, dễ đọc trong điều kiện ánh sáng yếu (học đêm).
- **Giọng điệu sản phẩm (Voice & Tone):**
  - **Bình tĩnh, dứt khoát, khích lệ:** Không dùng từ ngữ gây hoảng loạn ("Bạn sắp trượt rồi!"), thay vào đó là giọng điệu của một hoa tiêu cấp cứu ("Còn 14 thẻ đỏ để khóa điểm an toàn", "Bạn đã nắm chắc 65% khả năng qua môn, tiếp tục chặng 3").

---

## 8. Non-Goals (Explicit)

Để đảm bảo khả năng hoàn thiện nhanh và sắc bén trong v1, các phạm vi sau đây **hoàn toàn không thuộc mục tiêu**:
- **Không xây dựng máy chủ Backend riêng:** Không có tài khoản đăng nhập/đăng ký, không cơ sở dữ liệu trên cloud.
- **Không hỗ trợ nhận diện chữ viết tay mờ/nghệch ngoặc:** Chỉ chấp nhận tài liệu in, đề cương xuất bản điện tử hoặc slide chuẩn.
- **Không hỗ trợ mạng xã hội / chia sẻ thẻ:** Không có tính năng chia sẻ bộ thẻ giữa các sinh viên trong v1.
- **Không thay thế giáo trình toàn diện:** Không cố gắng tóm tắt 100% nội dung sách dày 300 trang.

---

## 9. MVP Scope

### 9.1 In Scope (v1)
- Thiết lập Chiến dịch sinh tồn với đồng hồ đếm ngược và chọn 4 Nhóm môn học.
- Quét và nạp tài liệu từ Camera, Thư viện ảnh, tệp PDF và file DOC/DOCX (tối đa 20 trang).
- On-device OCR trích xuất văn bản và bounding box với Google ML Kit.
- Phân loại ER Triage (Code Red / Yellow / Green) và sinh Micro-Card 3 dòng bằng Gemini API (Client-side BYOK).
- Màn hình Buồng lái sinh tồn: Đếm ngược thời gian thực, Thước đo xác suất an toàn, phân bổ chặng 15 phút.
- Giao diện học thẻ Active Recall có ẩn từ khóa (Cloze Deletion) và tính năng Đối chiếu nguồn (Source-Grounding) 1 chạm.
- Chế độ Emergency Speed-run cho 10 phút trước giờ thi.
- Lưu trữ offline toàn bộ tiến độ bằng Room DB.

### 9.2 Out of Scope for MVP (Deferred to v2)
- Widget đếm ngược và trả lời quiz trên Màn hình khóa (Lockscreen Gatekeeper).
- Hỏi đáp giọng nói mô phỏng vấn đáp phòng thi (Socratic Voice Examiner).
- Đồng bộ đám mây đa thiết bị (Cloud sync qua Google Drive / Supabase).
- Mua gói API Key dùng ngay trong app (In-App Purchase) cho người không muốn tự tạo Google AI Studio Key.

---

## 10. Success Metrics

### 10.1 Chỉ số Chính (Primary Metrics)
- **SM-1 (Tỷ lệ hoàn thành chặng Red):** $\ge 75\%$ người dùng kích hoạt chiến dịch hoàn thành toàn bộ các thẻ Code Red trước thời điểm thi $T_0$. (Đánh giá FR-8, FR-9, FR-11).
- **SM-2 (Thời gian từ khi mở app đến khi có thẻ học đầu tiên):** $\le 90$ giây cho một tài liệu 10 trang (bao gồm cả khâu quét OCR và AI phản hồi). (Đánh giá FR-2, FR-4, FR-5).

### 10.2 Chỉ số Phụ (Secondary Metrics)
- **SM-3 (Tần suất sử dụng tính năng Đối chiếu nguồn):** Trung bình $\ge 2$ lượt chạm mở xem tài liệu gốc trên mỗi 10 thẻ học, chứng minh tính năng Source-grounding mang lại giá trị củng cố niềm tin. (Đánh giá FR-15).

### 10.3 Chỉ số Đối trọng (Counter-metrics - Tuyệt đối không tối ưu sai hướng)
- **SM-C1 (Số lượng thẻ được sinh ra):** Không tối ưu tăng số lượng thẻ. Mục tiêu là càng ít thẻ càng tốt (tối đa 35–45 thẻ cho 20 trang tài liệu). Sinh quá nhiều thẻ đồng nghĩa với việc thất bại trong việc chắt lọc Pareto 80/20.
- **SM-C2 (Thời gian người dùng ở lại trong app):** Không tìm cách giữ chân người dùng trong app lâu (time spent). Ngược lại, mục tiêu là giúp người dùng học thuộc nhanh nhất để nghỉ ngơi hoặc đi thi.

---

## 11. Open Questions

1. **[OQ-1]** Đối với tệp DOC/DOCX không có hình ảnh trang in, trải nghiệm giao diện Source-grounding nên hiển thị dưới dạng khung xem văn bản phân đoạn hay tự động render thành trang mô phỏng?
2. **[OQ-2]** Hạn mức Rate Limit của Google Gemini Free Tier hiện tại là 15 RPM (Requests Per Minute). Khi người dùng gửi một đợt tài liệu 20 trang, cơ chế ghép các trang thành một payload duy nhất hay chia nhỏ theo từng batch để tối ưu chi phí token? [Đã giải quyết sơ bộ: Ghép toàn bộ text 20 trang vào 1 single prompt duy nhất].

---

## 12. Assumptions Index

Mọi giả định được suy luận trong quá trình lập PRD theo Fast Path:
- **[ASSUMPTION §2.2]**: v1 loại trừ tài liệu viết tay mờ nhòe do độ chính xác của ML Kit Text Recognition với chữ viết tay tự do chưa đủ độ tin cậy trong tình huống khẩn cấp.
- **[ASSUMPTION §4.1 / FR-1]**: Giới hạn thời gian chiến dịch tối đa là 7 ngày để giữ đúng bản sắc ứng dụng sinh tồn cấp tốc 48h.
- **[ASSUMPTION §4.1 / FR-3]**: Giới hạn 10.000 từ đối với file DOC/DOCX tương đương dung lượng ~20 trang A4 chuẩn.
- **[ASSUMPTION §4.6 / FR-16]**: Người dùng là sinh viên đại học / học sinh cấp 3 có khả năng thực hiện 3 bước tạo Google AI Studio API Key theo hướng dẫn trực quan trong app.
- **[ASSUMPTION §7]**: Chế độ Dark Mode mặc định được người dùng ưa chuộng hơn vì phần lớn thời gian ôn thi cấp tốc 48h diễn ra vào ban đêm.
