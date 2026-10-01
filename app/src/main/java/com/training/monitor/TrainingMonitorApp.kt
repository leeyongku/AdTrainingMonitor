// Hilt 의존성 그래프의 진입점 — 앱 전역에서 @Inject로 주입받을 수 있게 하는 Application
package com.training.monitor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrainingMonitorApp : Application()
