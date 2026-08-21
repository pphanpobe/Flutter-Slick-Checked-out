# Slick BT Keyboard

แอป Android ที่เปลี่ยนมือถือให้เป็น **คีย์บอร์ดบลูทูธ** สำหรับคอมพิวเตอร์
โดยใช้ **แอปคีย์บอร์ดที่ติดตั้งอยู่บนเครื่องอยู่แล้ว** (Gboard, SwiftKey, Samsung Keyboard ฯลฯ)
เป็นตัวป้อนข้อมูล — พิมพ์บนมือถือ ตัวอักษรวิ่งไปโผล่ที่คอมทันที

คอมมองเห็นมือถือเป็นคีย์บอร์ด HID จริง ๆ จึงไม่ต้องลงไดรเวอร์หรือแอปฝั่งคอมเลย
ใช้ได้กับ Windows / macOS / Linux / Android TV / iPad และแม้แต่หน้าจอ BIOS

---

## ทำงานยังไง

Android มี API ชื่อ `BluetoothHidDevice` (ตั้งแต่ Android 9 / API 28) ที่ให้เครื่องประกาศตัวเป็น
อุปกรณ์ HID *ฝั่งอุปกรณ์* ได้ แอปนี้ลงทะเบียน SDP record เป็นคีย์บอร์ด แล้วส่ง report
ขนาด 8 ไบต์แบบ boot-protocol ทุกครั้งที่ผู้ใช้กดปุ่ม

ส่วนที่ทำให้ "ใช้คีย์บอร์ดของเครื่องเราเอง" ได้คือ `KeyCaptureView`:
เป็น `View` ที่บอกระบบว่าตัวเองเป็นช่องกรอกข้อความ แต่ไม่มีบัฟเฟอร์ข้อความจริง ๆ
ทุก `commitText` / `setComposingText` / `deleteSurroundingText` ที่ IME ส่งเข้ามา
จะถูกแปลงเป็น HID report แล้วยิงออกบลูทูธทันที แทนที่จะเก็บไว้ในตัวเอง

```
IME ของผู้ใช้ ──▶ KeyCaptureView ──▶ UsKeymap / ThaiKedmanee ──▶ BtHidService ──▶ คอม
   (Gboard)        (InputConnection)      (char → HID usage)        (HID report)
```

## ฟีเจอร์

- พิมพ์ด้วยคีย์บอร์ดตัวโปรดของตัวเอง ไม่ต้องเรียนรู้ layout ใหม่
- ปุ่มพิเศษ: Esc, Tab, Enter, Backspace, Delete, ลูกศร, Home/End, PgUp/PgDn, PrtSc, Menu
- ปุ่ม F1–F12
- Modifier แบบ sticky: **แตะ** = ใช้กับปุ่มถัดไปครั้งเดียว, **กดค้าง** = ล็อกค้างไว้
  (เช่น Ctrl แล้วตามด้วย C, หรือ Win แล้วตามด้วย R)
- "ส่งข้อความยาว" — วางข้อความทั้งก้อนแล้วให้แอปพิมพ์ให้
- รองรับภาษาไทยผ่านผัง **เกษมณี (Kedmanee)** — ดูข้อจำกัดด้านล่าง
- ทำงานเป็น foreground service การเชื่อมต่อจึงไม่หลุดเวลาสลับแอป

## วิธีใช้

1. เปิดแอป กด **เริ่มระบบ** แล้วอนุญาตสิทธิ์บลูทูธ
2. กด **ให้คอมมองเห็น** แล้วไปจับคู่จากฝั่งคอม (คอมจะเห็นเป็นคีย์บอร์ด)
3. กลับมาที่แอป กด **เลือกอุปกรณ์** แล้วเลือกคอมที่จับคู่ไว้
4. เมื่อขึ้นว่าเชื่อมต่อแล้ว ให้แตะที่กล่องกลางจอ แล้วพิมพ์ได้เลย

## ข้อจำกัดที่ควรรู้

- **ต้องเป็น Android 9 ขึ้นไป** และผู้ผลิตต้องเปิดใช้ HID Device profile ไว้
  (เครื่องส่วนใหญ่เปิด แต่ ROM บางตัวปิด — ถ้าลงทะเบียนไม่สำเร็จแอปจะแจ้ง)
- คีย์บอร์ดบลูทูธส่ง *ตำแหน่งปุ่ม* ไม่ได้ส่งตัวอักษร ฝั่งคอมเป็นคนตัดสินว่าจะได้ตัวอะไร
  ดังนั้นการพิมพ์ไทยจะถูกแปลงเป็นตำแหน่งปุ่มตามผังเกษมณี และ**คอมต้องสลับเป็นภาษาไทยเอง**
  ส่วนภาษาอังกฤษจะถูกแปลงตามผัง US เช่นกัน — พิมพ์ไทยสลับอังกฤษต้องสลับภาษาที่ฝั่งคอม
- ตัวอักษรที่ไม่มีตำแหน่งบนผัง US หรือเกษมณี (เช่น emoji) จะถูกข้ามและแจ้งจำนวนที่ส่งไม่ได้
- IME บางตัวยังพยายาม autocorrect ถึงแม้จะตั้ง `TYPE_TEXT_FLAG_NO_SUGGESTIONS` แล้ว
  แอปจัดการโดย mirror composing region ไว้แล้วส่ง backspace ย้อนกลับให้อัตโนมัติ

## Build

ไฟล์ APK ถูก build อัตโนมัติด้วย GitHub Actions ทุกครั้งที่มี push
(ดู [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml))

โหลดได้จากแท็บ **Actions → เลือก run ล่าสุด → Artifacts**:

| Artifact | ไฟล์ |
| --- | --- |
| `slick-bt-keyboard-debug` | `slick-bt-keyboard-debug.apk` |
| `slick-bt-keyboard-release` | `slick-bt-keyboard-release.apk` |

ถ้า push tag ที่ขึ้นต้นด้วย `v` (เช่น `v1.0`) workflow จะสร้าง GitHub Release แนบ APK ให้ด้วย

Build เองบนเครื่อง:

```bash
./gradlew assembleDebug
# ได้ไฟล์ที่ app/build/outputs/apk/debug/app-debug.apk
```

> หมายเหตุ: release build เซ็นด้วย debug keystore เพื่อให้ CI ปล่อย APK ที่ติดตั้งได้ทันที
> ถ้าจะเอาขึ้น Play Store ต้องเปลี่ยนไปใช้ keystore จริงใน `app/build.gradle.kts`

## โครงสร้างโค้ด

| ไฟล์ | หน้าที่ |
| --- | --- |
| `HidSpec.kt` | HID report descriptor และ usage code |
| `UsKeymap.kt` | แปลงตัวอักษร / Android key code → HID usage |
| `ThaiKedmanee.kt` | ตารางผังแป้นพิมพ์ไทยเกษมณี |
| `KeyCaptureView.kt` | ดักอินพุตจาก IME ของผู้ใช้ |
| `BtHidService.kt` | ลงทะเบียน HID profile, จัดการการเชื่อมต่อ, คิวส่ง report |
| `MainActivity.kt` | หน้าจอหลัก ปุ่มพิเศษ modifier |

`legacy/home_page.dart` เป็นไฟล์เดิมที่อยู่ใน repo ก่อนหน้านี้ ไม่เกี่ยวกับแอปนี้
