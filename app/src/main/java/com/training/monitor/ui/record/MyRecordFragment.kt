// 대원 본인 기록 조회 화면 - 종목별 탭 + 추이 그래프 + 기록 목록

package com.training.monitor.ui.record

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.tabs.TabLayout
import com.training.monitor.data.model.RecordDto
import com.training.monitor.data.model.TrendPoint
import com.training.monitor.databinding.FragmentMyRecordBinding
import com.training.monitor.ui.photo.PhotoViewActivity

/**
 * 대원(MEMBER 역할) 전용 화면: [tabCategory]로 종목을 고르면, 그 종목 기준으로
 * 추이 꺾은선 그래프(전체 기간) + 개인 최고 기록 + 측정 기록 목록을 함께 보여준다.
 * MPAndroidChart 라이브러리의 [com.github.mikephil.charting.charts.LineChart]를 사용한다.
 *
 * [MVVM 변경] 이 클래스는 이제 "화면을 그리는 View" 역할만 한다. 서버 통신과 조회 결과 보관은
 * [MyRecordViewModel]이 담당하고, 여기서는 그 데이터를 종목별로 걸러 요약 텍스트/목록/차트로
 * 변환해 그리기만 한다.
 */
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

    /** 추이 데이터 포인트 목록을 MPAndroidChart의 Entry로 변환해 꺾은선 그래프를 그린다. */
    private fun drawTrendChart(points: List<TrendPoint>) {
        // x축은 측정 순서(인덱스), y축은 측정값 — 실제 날짜 라벨은 별도 포맷터 없이 순번만 사용
        val entries = points.mapIndexed { index, point ->
            Entry(index.toFloat(), point.value.toFloat())
        }

        val dataSet = LineDataSet(entries, "기록 추이").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            animateX(500)
            invalidate()   // 데이터 변경 후 차트를 강제로 다시 그림
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
