// 대원 본인 기록 목록(종목별 필터링된) RecyclerView 어댑터

package com.training.monitor.ui.record

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.training.monitor.R
import com.training.monitor.data.model.RecordDto
import com.training.monitor.databinding.ItemRecordBinding

/**
 * [RecordDto] 목록을 RecyclerView에 표시하는 어댑터. [MyRecordFragment]와 관리자용
 * [RecordListViewFragment]가 공유해서 쓴다. [onPhotoClick]은 사진이 있는 항목의 카메라
 * 아이콘을 탭했을 때 호출되며, 실제 화면 전환(PhotoViewActivity 실행)은 호출부가 담당한다.
 * [onDeleteClick]은 관리자 화면([RecordListViewFragment])에서만 넘겨준다 — null이면(기본값,
 * [MyRecordFragment]가 쓰는 경우) 삭제 아이콘 자체를 숨겨 대원 본인은 삭제할 수 없게 한다.
 */
class RecordAdapter(
    private val onPhotoClick: (RecordDto) -> Unit = {},
    private val onDeleteClick: ((RecordDto) -> Unit)? = null
) : ListAdapter<RecordDto, RecordAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /** 측정 기록 한 건의 데이터를 뷰에 바인딩한다. */
        fun bind(record: RecordDto) {
            binding.tvGrade.text = record.grade ?: "-"
            binding.tvGrade.background.mutate().setTint(gradeColor(binding.root.context, record.grade))
            binding.tvCategory.text = record.categoryName
            binding.tvDate.text = record.measuredAt ?: "측정일 없음"
            binding.tvValue.text = "${formatValue(record.value)}${record.unit}"
            binding.ivPhotoIcon.visibility = if (record.hasPhoto) View.VISIBLE else View.GONE
            binding.ivPhotoIcon.setOnClickListener { onPhotoClick(record) }
            binding.ivDeleteRecord.visibility = if (onDeleteClick != null) View.VISIBLE else View.GONE
            binding.ivDeleteRecord.setOnClickListener { onDeleteClick?.invoke(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    /** ListAdapter가 리스트 갱신 시 항목 동일성/내용 변경 여부를 판단하는 데 사용하는 콜백. */
    companion object DiffCallback : DiffUtil.ItemCallback<RecordDto>() {
        override fun areItemsTheSame(a: RecordDto, b: RecordDto) = a.id == b.id
        override fun areContentsTheSame(a: RecordDto, b: RecordDto) = a == b

        // 등급별 고정 색상 — 특급(우수)일수록 파란 계열, 불합격일수록 붉은 계열 (StatsFragment와 동일한 배색).
        fun gradeColor(context: Context, grade: String?) = when (grade) {
            "특급" -> ContextCompat.getColor(context, R.color.grade_top)
            "1급" -> ContextCompat.getColor(context, R.color.grade_1)
            "2급" -> ContextCompat.getColor(context, R.color.grade_2)
            "3급" -> ContextCompat.getColor(context, R.color.grade_3)
            "불합격" -> ContextCompat.getColor(context, R.color.color_danger)
            else -> Color.GRAY
        }

        /** 측정값이 정수면 소수점 없이("700"), 아니면 그대로("70.5") 표시한다. */
        fun formatValue(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}
