# Phần mềm Hệ thống Tính điểm Trực tiếp cho các Cuộc thi Robotics

Kho lưu trữ này chứa mã nguồn của hệ thống tính điểm trực tiếp được thiết kế cho các cuộc thi robotics. Hệ thống cho phép theo dõi và hiển thị điểm số, hiệu suất của đội và số liệu thống kê trận đấu trong thời gian thực.

## Hướng dẫn Thiết lập

### Giới thiệu về Hệ thống Tính điểm Trực tiếp

Hệ thống Tính điểm Trực tiếp là một ứng dụng chạy trên nền tảng web, cung cấp các cập nhật theo thời gian thực về điểm số và số liệu thống kê trong suốt cuộc thi. Hệ thống được thiết kế thân thiện với người dùng và có thể truy cập được từ nhiều loại thiết bị khác nhau, bao gồm máy tính để bàn, máy tính bảng và điện thoại thông minh.

**Ai sử dụng Hệ thống Tính điểm Trực tiếp?**

* **Ban tổ chức sự kiện:** Để quản lý và hiển thị điểm số trong suốt cuộc thi, đảm bảo sự kiện vận hành trơn tru và thu hút khán giả.
* **Người ghi điểm (Scorekeepers):** Để nhập điểm và cập nhật thông tin trận đấu trong thời gian thực.
* **Các đội thi và Khán giả:** Để xem điểm trực tiếp và số liệu thống kê trận đấu được hiển thị trên các màn hình lớn tại sự kiện.

### Yêu cầu Phần cứng

Để vận hành hệ thống một cách hiệu quả nhất, các phần cứng sau đây được khuyến nghị:

**Màn hình hiển thị**:

* Màn hình hoặc máy chiếu lớn để hiển thị điểm cho khán giả, ưu tiên cổng HDMI và kích thước từ 27 inch trở lên để có tầm nhìn tốt hơn.
* Đối với phiên bản phần mềm này, khuyến nghị có ít nhất một màn hình cho mỗi sân thi đấu để hiển thị điểm trực tiếp và bộ đếm giờ tại sân.

**Máy tính bảng**:

* Cả máy tính bảng Android và iOS đều tương thích với phần mềm FTC-Live. Thông số kỹ thuật máy tính bảng khuyến nghị bao gồm:
* Kích thước màn hình: 9 inch trở lên.
* Hệ điều hành: Android 8.0+ hoặc iOS 12.0+.
* Kết nối: Khả năng kết nối Wi-Fi để truy cập mạng.
* Ứng dụng: Sử dụng trình duyệt Chrome để có độ tương thích tốt nhất.



**Mạng (Network)**:

* Sử dụng mạng cục bộ (LAN) chuyên dụng để kết nối các thiết bị tính điểm, đảm bảo kết nối an toàn và ổn định.
* Để đảm bảo an ninh hệ thống, ngăn chặn truy cập trái phép từ bên ngoài, khuyến nghị sử dụng mạng nội bộ và thiết lập chính sách hạn chế truy cập chỉ dành cho các thiết bị được ủy quyền.
* Sử dụng kết nối có dây (Ethernet) bất cứ khi nào có thể. *Lưu ý: Máy tính bảng của trọng tài tính điểm thời gian thực là thiết bị không dây.*

### Yêu cầu Hệ thống và Kiến trúc Vận hành

Hệ thống Tính điểm Trực tiếp được thiết kế để hoạt động trên các nền tảng Windows và macOS. Dưới đây là các yêu cầu hệ thống và kiến trúc vận hành:

![Operation Architecture](docs/image/operation-archtecture.jpg)

**Yêu cầu Hệ thống**:

* Hệ điều hành: Windows 10 hoặc macOS 10.15 (Catalina) trở lên.
* Trình duyệt Web: Google Chrome (khuyến nghị phiên bản mới nhất).
* RAM tối thiểu 4 GB (khuyến nghị 8 GB).
* 500 MB dung lượng đĩa trống.
* Bộ chuyển đổi Ethernet (khuyến nghị cho các kết nối có dây).

**Yêu cầu đối với Máy tính bảng**:

* Hệ điều hành: Android 8.0+ hoặc iOS 12.0+.
* Trình duyệt Web: Google Chrome (khuyến nghị phiên bản mới nhất).
* Khả năng Wi-Fi để truy cập mạng.

Trong thực tế, Hệ thống Tính điểm Trực tiếp có thể được vận hành linh hoạt theo các kiến trúc khác nhau tùy theo quy mô và yêu cầu của sự kiện. Các yêu cầu phần cứng và hệ thống nêu trên đảm bảo hiệu suất tối ưu và độ tin cậy trong suốt cuộc thi.

**Kiến trúc Vận hành**:

Cấu hình thường được sử dụng nhất như sau:

1. **Laptop Server tính điểm**: Laptop này lưu trữ ứng dụng Hệ thống Tính điểm Trực tiếp và quản lý toàn bộ dữ liệu tính điểm. Nó nên được kết nối với mạng cục bộ qua cáp Ethernet để đảm bảo tính ổn định.
2. **Màn hình hiển thị tại sân**: Các màn hình này được kết nối với laptop server và hiển thị điểm số, thông tin trận đấu thời gian thực cho khán giả.
3. **Máy tính bảng của trọng tài**: Được các trọng tài sử dụng để nhập điểm và cập nhật thông tin trận đấu. Các máy này kết nối với laptop server qua mạng cục bộ, ưu tiên sử dụng Wi-Fi.
4. **Switch/Router mạng**: Một thiết bị chuyển mạch hoặc bộ định tuyến mạng chuyên dụng được sử dụng để tạo mạng nội bộ cho hệ thống, đảm bảo liên lạc an toàn và tin cậy giữa các thiết bị.

### Tải xuống Phần mềm Hệ thống Tính điểm Trực tiếp

Bạn có thể tải phiên bản mới nhất của phần mềm từ mục releases của kho lưu trữ này:
[Releases - Live Scoring System](https://github.com/lgthevinh/scoring-system/releases)

Hãy đảm bảo tải đúng phiên bản cho sự kiện bạn đang tổ chức (ví dụ: FANROC, FARC, v.v.). Và hãy đọc kỹ ghi chú phát hành (release notes) để tải phiên bản ổn định mới nhất.

**Mẹo quan trọng khi khởi chạy phần mềm hệ thống**:

* Đảm bảo Google Chrome đã được cài đặt và là trình duyệt mặc định.
* **Quan trọng**: Trình duyệt Firefox KHÔNG được hỗ trợ!
* Tắt mọi tường lửa/phần mềm diệt virus trên máy tính chạy server.
* Tắt các phần mềm chặn quảng cáo trên các thiết bị sẽ giao tiếp với server. Phần mềm chặn quảng cáo đôi khi hiểu nhầm tài nguyên của server là quảng cáo.
* Xóa bộ nhớ đệm (cache) trình duyệt.

Để chạy Hệ thống Tính điểm Trực tiếp, hãy làm theo các bước sau:

1. Giải nén tệp ZIP đã tải xuống vào vị trí mong muốn trên máy tính của bạn.
2. Điều hướng đến vị trí tệp chạy theo đường dẫn thư mục sau:

```text
{extracted_folder}
    /vrc-scoring-system/
        run.bat (cho Windows)
        run.sh (cho macOS)

```

3. Thực thi tệp chạy phù hợp với hệ điều hành của bạn:
* Đối với Windows: Nhấp đúp vào `run.bat`
* Đối với macOS: Mở terminal, điều hướng đến thư mục và chạy lệnh `sh run.sh`

4. Server của Hệ thống Tính điểm Trực tiếp sẽ khởi động, và bạn sẽ thấy một thông báo cho biết server đang chạy.

![Live Scoring System Running](docs/image/run-system.jpg)

Truy cập Hệ thống Tính điểm Trực tiếp bằng cách nhập địa chỉ IP của máy chủ vào thanh địa chỉ của trình duyệt. Ví dụ: `http://{your_host_machine_ip}` hoặc URL được cung cấp trong đầu ra của terminal.

### Đăng nhập vào Hệ thống Tính điểm Trực tiếp

Sau khi truy cập URL của Hệ thống Tính điểm Trực tiếp, bạn sẽ được yêu cầu đăng nhập. Sử dụng thông tin đăng nhập mặc định sau nếu đăng nhập từ máy chủ:

* **Tên đăng nhập (Username)**: local

Không yêu cầu mật khẩu đối với truy cập nội bộ (local access).

![Login Screen](docs/image/login-screen.jpg)


### Hướng dẫn Người dùng

Sau khi đăng nhập, bạn sẽ được chuyển hướng đến trang chính, tại đây bạn sẽ làm theo các bước dưới đây để thiết lập và vận hành hệ thống tính điểm cho sự kiện của mình.

#### Chuẩn bị sự kiện

Điều hướng đến phần `Event dashboard` để thiết lập các thông tin chi tiết về sự kiện của bạn, bao gồm các đội thi, trận đấu và quy tắc tính điểm.

![Event Dashboard](docs/image/event-dashboard-screen.jpg)

Trong Bảng điều khiển Sự kiện (Event Dashboard), bạn có thể:

* Tạo và quản lý thông tin sự kiện, và đặt sự kiện đang hoạt động (active) cho hệ thống.
* Điều hướng đến tab `Event Tools` để tạo tài khoản cho người ghi điểm và trọng tài, thêm/nhập danh sách đội và tạo lịch thi đấu.

Kích hoạt sự kiện cho hệ thống bằng cách nhấp vào nút `Set Active`.

#### Trong khi diễn ra sự kiện

Sau khi thiết lập sự kiện, hãy điều hướng đến màn hình Chính (Main screen), tại đây bạn có thể xem thông tin về lịch thi đấu, bảng xếp hạng, kết quả cùng các tùy chọn khác.

![Main Screen](docs/image/main-page.jpg)

#### Sau sự kiện

Sau sự kiện, bạn có thể xuất kết quả trận đấu và bảng xếp hạng để lưu trữ và phân tích. Tại thư mục chứa tệp chạy, bạn sẽ tìm thấy tệp cơ sở dữ liệu của sự kiện dưới tên `{MÃ_SỰ_KIỆN}.db`. Để xem điểm chi tiết của từng trận đấu, hãy điều hướng đến thư mục `files`, bạn sẽ tìm thấy các tệp JSON của mỗi trận đấu.

### Xử lý sự cố

Nếu bạn gặp bất kỳ sự cố nào khi sử dụng Hệ thống Tính điểm Trực tiếp, hãy tham khảo phần xử lý sự cố trong tài liệu hoặc tạo một "issue" trong kho lưu trữ để được hỗ trợ.

Hoặc liên hệ với nhà phát triển tại:

* Email: `everwellmax@gmail.com` (Người duy trì chính thức của hệ thống tính điểm trực tiếp)
* Email: `hirovn9395@gmail.com`  (Người duy trì chính thức của hệ thống tính điểm trực tiếp)

Liên hệ hỗ trợ phần mềm cho sự kiện cụ thể:

* Email: `truongcongminhquan09@gmail.com` (Người duy trì hệ thống tính điểm trực tiếp cho sự kiện FANROC)
