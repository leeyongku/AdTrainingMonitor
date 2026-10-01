// 관리자 - 부대 통계 및 그래프 화면

package com.training.monitor.ui.stats

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.data.*
import com.training.monitor.R
import com.training.monitor.databinding.FragmentStatsBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * 관리자 전용 화면: 지정 기간의 부대 통계를 원형 그래프(등급 분포)와
 * 막대 그래프(종목별 등급 분포)로 시각화한다. MPAndroidChart 라이브러리 사용.
 *
 * [MVVM 변경] 이 클래스는 이제 "화면을 그리는 View" 역할만 한다. 조회 기간 상태와 통계
 * API 호출은 [StatsViewModel]이 담당하고, 여기서는 그 결과를 실제 차트로 그리는 일만 한다.
 */
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels()

    // 날짜 라벨(tvDateFrom/tvDateTo) 표시용 포맷터. ViewModel 내부에서 API 호출용으로 쓰는
    // 포맷터와 형식은 같지만, "화면에 어떻게 보여줄지"는 View의 관심사라 별도로 둔다.
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // 계층 스피너에 고정으로 표시할 5단계 명칭. UnitDto.level(0~4)의 인덱스와 그대로 대응한다.
    private val levelLabels = listOf("여단", "대대", "중대", "소대", "분대")

    // 최초 부대 목록 로딩이 끝나 자동으로 통계를 한 번 조회했는지 여부.
    // 이후 계층/부대 선택 변경은 "조회" 버튼을 눌러야 반영되도록(기존 날짜 선택과 동일한 동작) 막는다.
    private var hasLoadedInitialStats = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 뷰가 생성된 직후 호출된다. 여기서 하는 일은 크게 세 가지다.
     * 1. 계층 스피너("여단/대대/.../분대")를 고정 목록으로 채우고, 선택이 바뀔 때마다
     *    ViewModel에 알려 그 계층에 속한 부대 목록을 다시 조회하게 한다.
     * 2. ViewModel의 각 LiveData(부대 목록, 조회 기간, 통계 결과, 토스트 메시지)를 관찰해
     *    값이 바뀔 때마다 화면 요소(스피너/라벨/차트)를 갱신한다 — 실제 네트워크 호출이나
     *    상태 보관은 전부 [StatsViewModel]의 책임이고, 이 함수는 "그 결과를 어떻게 그릴지"만
     *    담당한다(MVVM에서 View가 맡는 역할).
     * 3. 날짜 라벨/조회 버튼에 클릭 리스너를 연결한다.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 계층 스피너는 서버 조회 없이 항상 고정된 5단계(levelLabels)로 채운다 — 이 값
        // 자체는 UnitDto.level(0~4)과 1:1로 대응하는 상수라 매번 서버에 물어볼 필요가 없다.
        binding.spinnerLevel.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, levelLabels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 계층을 바꾸면(예: "대대" -> "중대") 그 계층에 속한 실제 부대 목록 자체가 달라지므로,
        // 스피너 선택 위치(position)를 그대로 level 값으로 넘겨 ViewModel에 재조회를 요청한다.
        // 그 결과는 아래 unitsForLevel observe 콜백에서 spinnerUnit에 채워진다.
        binding.spinnerLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override  fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setLevel(position)
            }
            // 스피너는 항상 기본 선택값이 있어서 실제로 "선택 없음" 상태가 되는 경우가 없다.
            // 인터페이스 구현을 위해 빈 채로 둔다.
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 계층이 바뀔 때마다(혹은 최초 진입 시) ViewModel이 그 계층의 부대 목록을 새로 내려주면,
        // 부대 선택 스피너(spinnerUnit)를 그 목록으로 다시 채운다.
        viewModel.unitsForLevel.observe(viewLifecycleOwner) { units ->
            val labels = units.map { it.name }
            binding.spinnerUnit.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, labels
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            // 화면에 막 진입했을 때만("총 0명" 같은 빈 화면으로 시작하지 않도록) 첫 번째
            // 부대를 자동으로 조회한다. 이후 계층/부대를 바꿔도 hasLoadedInitialStats가
            // true로 남아있어 자동 재조회는 더 이상 일어나지 않고, 사용자가 "조회" 버튼을
            // 눌러야만 통계가 갱신된다(날짜 선택과 동일하게, 선택 변경 즉시 조회하지 않는
            // 일관된 동작을 주기 위함).
            if(!hasLoadedInitialStats && units.isNotEmpty()){
                hasLoadedInitialStats = true
                viewModel.loadStats(units[0].id)
            }
        }

        // [MVVM 변경] 기존에는 dateFrom/dateTo가 바뀔 때마다 updateDateLabels()를 직접 호출했다.
        // 이제는 ViewModel의 LiveData를 관찰해 값이 바뀔 때마다 라벨이 자동으로 갱신된다.
        viewModel.dateFrom.observe(viewLifecycleOwner) { binding.tvDateFrom.text = it.format(fmt) }
        viewModel.dateTo.observe(viewLifecycleOwner) { binding.tvDateTo.text = it.format(fmt) }

        // [MVVM 변경] 통계 응답이 도착하면 두 차트를 다시 그리도록 관찰을 등록한다.
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                binding.tvMemberCount.text = stats.memberCount.toString()
                binding.tvRecordCount.text = stats.recordCount.toString()
                drawPieChart(stats.gradeDistribution)
                drawBarChart(stats.byCategory)
            }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        binding.tvDateFrom.setOnClickListener { showDatePicker(isFrom = true) }
        binding.tvDateTo.setOnClickListener { showDatePicker(isFrom = false) }
        // "조회" 버튼을 눌러야만 현재 스피너에서 선택된 부대 기준으로 통계를 다시 불러온다.
        // 스피너의 선택 위치(index)로 unitsForLevel 원본 리스트에서 실제 UnitDto를 찾아 id를 넘긴다.
        binding.btnSearch.setOnClickListener {
            val units = viewModel.unitsForLevel.value ?: emptyList()
            val unitIdx = binding.spinnerUnit.selectedItemPosition
            // 부대 목록이 비어있거나 스피너가 아직 갱신되기 전(-1) 같은 비정상 상태에서는
            // 조회를 시도하지 않고 조용히 무시한다.
            if (unitIdx < 0 || unitIdx >= units.size) return@setOnClickListener
            viewModel.loadStats(units[unitIdx].id)
        }
    }

    /**
     * 전체 등급 분포를 원형 그래프로 그린다.
     * @param distribution 등급명 -> 인원수 (예: {"특급": 3, "1급": 10, ...})
     */
    private fun drawPieChart(distribution: Map<String, Int>) {
        // 등급별 고정 색상 — 특급(우수)일수록 파란 계열, 불합격일수록 붉은 계열로 시각적 위계를 준다.
        val gradeColors = mapOf(
            "특급" to ContextCompat.getColor(requireContext(), R.color.grade_top),
            "1급" to ContextCompat.getColor(requireContext(), R.color.grade_1),
            "2급" to ContextCompat.getColor(requireContext(), R.color.grade_2),
            "3급" to ContextCompat.getColor(requireContext(), R.color.grade_3),
            "불합격" to ContextCompat.getColor(requireContext(), R.color.color_danger)
        )

        val entries = distribution.map { PieEntry(it.value.toFloat(), it.key) }
        // 위 맵에 없는 등급명이 오면(방어적으로) 회색 처리
        val colors = entries.map { gradeColors[it.label] ?: Color.GRAY }

        // entries list "특급" 에 해당하는 color int 가져와  colors  int List 배열에 넣는다

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 12f
            valueTextColor = Color.WHITE
        }

        binding.pieChart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true   // 도넛 형태로 표시
            holeRadius = 40f
            legend.isEnabled = true
            animateY(600)
            invalidate()
        }
    }

    /**
     * 종목별 등급 분포를 그룹 막대 그래프로 그린다. x축은 종목, 각 x축 위치마다
     * 등급 5개(특급~불합격)가 나란히 묶여서 표시된다.
     * @param byCategory 종목명 -> (등급명 -> 인원수)
     */
    private fun drawBarChart(byCategory: Map<String, Map<String, Int>>) {
        val grades = listOf("특급", "1급", "2급", "3급", "불합격")
        val categories = byCategory.keys.toList()
        val colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.grade_top),
            ContextCompat.getColor(requireContext(), R.color.grade_1),
            ContextCompat.getColor(requireContext(), R.color.grade_2),
            ContextCompat.getColor(requireContext(), R.color.grade_3),
            ContextCompat.getColor(requireContext(), R.color.color_danger)
        )

        // 등급마다 하나의 BarDataSet을 만들고, 각 데이터셋 안에서 종목(x축) 순서로 값을 채운다.
        // 즉 dataSets[등급 인덱스].entries[종목 인덱스] 구조로, 그룹 막대 그래프의 "계열"에 해당한다.
        val dataSets = grades.mapIndexed { i, grade ->
            val entries = categories.mapIndexed { j, cat ->
                BarEntry(j.toFloat(), (byCategory[cat]?.get(grade) ?: 0).toFloat())
            }
            BarDataSet(entries, grade).apply { color = colors[i] }
        }

        // MPAndroidChart의 groupBars는 "groupSpace + 계열 수 x (barSpace + barWidth) == 1"을
        // 만족해야 그룹(종목)별로 막대가 올바르게 묶여서 x축 라벨과 정렬된다.
        // grades.size(5) 기준: 0.2 + 5 x (0.02 + 0.14) = 1.0
        val groupSpace = 0.2f
        val barSpace = 0.02f
        val barWidth = 0.14f
        val barData = BarData(dataSets).apply { this.barWidth = barWidth }

        binding.barChart.apply {
            data = barData
            description.isEnabled = false
            // x축 숫자 인덱스 대신 실제 종목명을 라벨로 표시
            xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(categories)
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = 0f + barData.getGroupWidth(groupSpace, barSpace) * categories.size
            xAxis.setCenterAxisLabels(true)
            // IndexAxisValueFormatter는 정수 인덱스 하나당 라벨 하나를 기대한다. granularity를 지정하지
            // 않으면 종목 수에 따라 축이 0.5 같은 분수 간격으로 눈금을 나눠, 같은 라벨이 중복 표시된다.
            xAxis.granularity = 1f
            xAxis.isGranularityEnabled = true
            // 등급(계열) 5개를 x축 위치별로 묶어서 배치 (막대 간격 파라미터, 위 groupSpace/barSpace와 동일해야 함)
            groupBars(0f, groupSpace, barSpace)
            animateY(600)
            invalidate()
        }
    }

    /** 조회 시작일/종료일 중 하나를 선택하는 날짜 선택 다이얼로그를 띄운다. */
    private fun showDatePicker(isFrom: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val date = LocalDate.of(y, m + 1, d)   // DatePickerDialog의 월(m)은 0부터 시작하므로 +1
            // [MVVM 변경] 기존에는 Fragment의 dateFrom/dateTo 프로퍼티에 직접 대입했다.
            // 이제는 ViewModel에 위임하고, 라벨 갱신은 위에서 등록한 observe가 자동으로 처리한다.
            if (isFrom) viewModel.setDateFrom(date) else viewModel.setDateTo(date)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
