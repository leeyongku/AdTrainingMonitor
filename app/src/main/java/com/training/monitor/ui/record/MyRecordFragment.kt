// 대원 본인 기록 조회 화면 - 종목별 탭 + 추이 그래프 + 기록 목록

package com.training.monitor.ui.record

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import com.training.monitor.data.model.RecordDto
import com.training.monitor.data.model.TrendPoint
import com.training.monitor.databinding.FragmentMyRecordBinding
import com.training.monitor.ui.photo.PhotoViewActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * 대원(MEMBER 역할) 전용 화면: [tabCategory]로 종목을 고르면, 그 종목 기준으로
 * 추이 꺾은선 그래프(전체 기간) + 개인 최고 기록 + 측정 기록 목록을 함께 보여준다.
 * MPAndroidChart 라이브러리의 [com.github.mikephil.charting.charts.LineChart]를 사용한다.
 *
 * [MVVM 변경] 이 클래스는 이제 "화면을 그리는 View" 역할만 한다. 서버 통신과 조회 결과 보관은
 * [MyRecordViewModel]이 담당하고, 여기서는 그 데이터를 종목별로 걸러 요약 텍스트/목록/차트로
 * 변환해 그리기만 한다.
 */
@AndroidEntryPoint
class MyRecordFragment : Fragment() {

    private var _binding: FragmentMyRecordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyRecordViewModel by viewModels()
    private val adapter = RecordAdapter(onPhotoClick = { record ->
        startActivity(PhotoViewActivity.newIntentForRecord(requireContext(), record.id))
    })

    // 종목 탭 순서 == 서버 categoryId(1: 3km 달리기, 2: 팔굽혀펴기, 3: 윗몸일으키기)와 그대로 대응한다.
    // RecordDto에는 categoryId가 없고 categoryName만 내려오므로, 기록 목록 필터링은 이 이름으로 한다.
    private data class CategoryTab(val id: Long, val categoryName: String)
    private val categories = listOf(
        CategoryTab(1L, "3km 달리기"),
        CategoryTab(2L, "팔굽혀펴기"),
        CategoryTab(3L, "윗몸일으키기")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecords.adapter = adapter

        categories.forEach { binding.tabCategory.addTab(binding.tabCategory.newTab().setText(it.categoryName)) }
        binding.tabCategory.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = updateForSelectedCategory()
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // [MVVM 변경] 데이터가 도착하면 자동으로 화면을 갱신하도록 먼저 관찰을 등록한 뒤 조회를 시작한다.
        // 종목별 필터링은 탭 선택 상태 + 전체 기록 목록을 함께 봐야 하므로, records가 갱신될 때도
        // (최초 로딩 시) 현재 선택된 탭 기준으로 다시 걸러 그린다.
        viewModel.records.observe(viewLifecycleOwner) { updateForSelectedCategory() }
        viewModel.trendPoints.observe(viewLifecycleOwner) { points -> drawTrendChart(points) }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        viewModel.loadRecords()
        updateForSelectedCategory()   // 탭 0번(3km 달리기) 기준으로 최초 추이 조회까지 트리거

        binding.btnDeleteMyCategoryRecords.setOnClickListener { showDeleteCategoryDialog() }
        binding.btnDeleteMyAllRecords.setOnClickListener { showDeleteAllDialog() }
    }

    /** 현재 선택된 종목 탭의 기록만 삭제하기 전, 되돌릴 수 없는 작업이므로 확인 다이얼로그를 띄운다. */
    private fun showDeleteCategoryDialog() {
        val category = categories.getOrNull(binding.tabCategory.selectedTabPosition) ?: categories[0]
        AlertDialog.Builder(requireContext())
            .setTitle("종목 기록 삭제")
            .setMessage("'${category.categoryName}' 기록을 전부 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ -> viewModel.deleteMyRecordsByCategory(category.id) }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 종목 구분 없이 내 기록 전부를 삭제하기 전, 되돌릴 수 없는 작업이므로 확인 다이얼로그를 띄운다. */
    private fun showDeleteAllDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("전체 기록 삭제")
            .setMessage("내 모든 측정 기록을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("전체 삭제") { _, _ -> viewModel.deleteAllMyRecords() }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 현재 선택된 탭의 종목을 기준으로, 전체 기록(viewModel.records)에서 해당 종목만 걸러
     * 요약 텍스트/개인 최고 기록/목록을 갱신하고, 그 종목의 추이 그래프를 새로 조회한다.
     */
    private fun updateForSelectedCategory() {
        val category = categories.getOrNull(binding.tabCategory.selectedTabPosition) ?: categories[0]
        val filtered = (viewModel.records.value ?: emptyList()).filter { it.categoryName == category.categoryName }

        adapter.submitList(filtered)
        binding.tvRecordSummary.text = "총 ${filtered.size}건의 기록"
        binding.tvPersonalBest.text = personalBestText(filtered)

        viewModel.loadTrend(category.id)
    }

    /** 종목 내 최고 기록을 "최고 기록: 값단위 (등급)" 형태로 만든다. 기록이 없으면 빈 문자열. */
    private fun personalBestText(records: List<RecordDto>): String {
        if (records.isEmpty()) return ""

        // 3km 달리기(초)는 낮을수록 좋고, 팔굽혀펴기/윗몸일으키기(회)는 높을수록 좋다 — unit으로 방향을 판단한다.
        val best = if (records.first().unit == "초") {
            records.minByOrNull { it.value }
        } else {
            records.maxByOrNull { it.value }
        } ?: return ""

        val gradeSuffix = best.grade?.let { " ($it)" } ?: ""
        return "최고 기록: ${RecordAdapter.formatValue(best.value)}${best.unit}$gradeSuffix"
    }

    /**
     * 추이 데이터 포인트 목록을 MPAndroidChart의 Entry로 변환해 꺾은선 그래프를 그린다.
     * x축에는 측정일을, 각 점 위의 값 라벨에는 측정값(+단위)을 표시한다.
     */
    private fun drawTrendChart(points: List<TrendPoint>) {
        // x축은 측정 순서(인덱스)를 그대로 쓰고, 실제 측정일 문자열은 x축 포맷터가 라벨로 바꿔치기한다.
        val entries = points.mapIndexed { index, point ->
            Entry(index.toFloat(), point.value.toFloat())
        }

        // 값 라벨에 단위(회/초)를 같이 보여주기 위해, 현재 선택된 종목의 단위를 기록 목록에서 찾아온다.
        val category = categories.getOrNull(binding.tabCategory.selectedTabPosition) ?: categories[0]
        //val unit = (viewModel.records.value ?: emptyList()).firstOrNull { it.categoryName == category.categoryName }?.unit ?: ""

        // orEmpty() 함수는 records.value 값이 있거나 Null 이면 빈문자열("")을 반환하고, null 이 아니면 자기 자신을 반환한다.
        // find 연산의 결과과 null 이 아닌 경우에만 우측의 프로퍼티(unit)에 접근한다.
        val unit = viewModel.records.value.orEmpty().find { it.categoryName == category.categoryName } ?.unit.orEmpty()

        val dataSet = LineDataSet(entries, "기록 추이").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
            // 점 위에 뜨는 값 라벨 — 측정값에 단위를 붙여서 보여준다 (예: "20초", "65회").
            valueFormatter = object : ValueFormatter() {
                override fun getPointLabel(entry: Entry?): String {
                    val value = entry?.y?.toDouble() ?: return ""
                    return "${RecordAdapter.formatValue(value)}$unit"
                }
            }
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false

            // x축 라벨을 "yyyy-MM-dd" 측정일에서 뒤 5자(MM-dd)만 잘라 표시한다.
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(points.map { it.measuredAt.takeLast(5) })
            }

            // y축(좌/우) 눈금 라벨은 단위를 붙이고 항상 정수로 표시한다 (예: "350초", "65회").
            // MPAndroidChart가 자동 계산한 눈금 간격은 소수(예: 62.5)일 수 있어, formatValue와 달리
            // 조건 없이 Math.round로 반올림한다.
            val yAxisFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    "${Math.round(value)}$unit"
            }
            axisLeft.valueFormatter = yAxisFormatter
            axisRight.valueFormatter = yAxisFormatter

            animateX(500)
            invalidate()   // 데이터 변경 후 차트를 강제로 다시 그림
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
