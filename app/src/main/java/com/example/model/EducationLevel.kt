package com.example.model

enum class EducationLevel(
    val displayName: String,
    val gradeSubtitle: String,
    val aiInstruction: String
) {
    PRIMARY(
        displayName = "Tiểu học",
        gradeSubtitle = "Lớp 1 – 5",
        aiInstruction = "Học sinh Tiểu học (lớp 1-5): giải thích thật đơn giản, trực quan, từng bước ngắn gọn, dùng ngôn từ thân thiện, tránh công thức phức tạp vượt cấp."
    ),
    MIDDLE_SCHOOL(
        displayName = "THCS",
        gradeSubtitle = "Lớp 6 – 9",
        aiInstruction = "Học sinh THCS (lớp 6-9): trình bày chặt chẽ theo chuẩn chương trình THCS và ôn thi vào 10, nêu rõ công thức, định lý hoặc ngữ pháp áp dụng."
    ),
    HIGH_SCHOOL(
        displayName = "THPT",
        gradeSubtitle = "Lớp 10 – 12",
        aiInstruction = "Học sinh THPT (lớp 10-12): trình bày chuẩn mực ôn thi cuối kỳ và tốt nghiệp THPT, chặt chẽ từng bước biến đổi, nhấn mạnh mẹo tránh mất điểm."
    ),
    UNIVERSITY(
        displayName = "Đại học",
        gradeSubtitle = "Cao đẳng & ĐH",
        aiInstruction = "Sinh viên Đại học / Cao đẳng: giải thích chính xác về mặt học thuật (Giải tích, Đại số, Xác suất, Lập trình, Kinh tế, v.v.), lập luận logic chặt chẽ và đầy đủ."
    );

    companion object {
        fun fromStored(value: String): EducationLevel {
            return entries.firstOrNull { it.name == value } ?: HIGH_SCHOOL
        }
    }
}
