package com.moyeota.presentation.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * 16 도착지 확인이 방 생성으로 넘기는 **조건의 목록**을 못 박는다.
 *
 * 왜 테스트가 필요한가: 반경 칩을 되살려도 화면은 멀쩡히 돌고 서버는 200 을 준다
 * (서버가 모르는 키를 흘리므로). 즉 **고른 값이 버려지는 상태에 아무 증상이 없다.**
 * 그래서 「조건은 정원뿐」을 여기서 고정한다 — 새 조건을 붙이려면 이 테스트를 함께 고쳐야 하고,
 * 그때 「서버가 그 값을 실제로 쓰는가」를 다시 묻게 된다.
 *
 * 짝이 되는 테스트는 data 쪽의 `PartyMappersTest.생성 요청 본문에는 좌표 라벨 정원만 실린다` 다
 * (요청 JSON 의 키 집합). 이쪽은 **화면이 들고 있는 조건**, 저쪽은 **전선에 실리는 본문**이다.
 */
class MatchConditionsTest {

    @Test
    fun `방 생성으로 넘기는 매칭 조건은 정원뿐이다`() {
        // kotlin-reflect 없이 보려고 자바 필드 목록을 쓴다(데이터 클래스의 프로퍼티와 1:1).
        // static 은 제외한다 — Compose 컴파일러가 `$stable` 을 얹는다(프로퍼티가 아니다).
        val fields = MatchConditions::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }

        assertEquals(listOf("capacity"), fields)
    }
}
