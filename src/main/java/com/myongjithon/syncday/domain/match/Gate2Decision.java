package com.myongjithon.syncday.domain.match;

/**
 * 게이트2(채팅 참여) 결정. 매칭이 성사된 <b>뒤</b> 각 유저가 상대와 채팅을 열지 정한다.
 * 게이트1({@link com.myongjithon.syncday.domain.analysis.MatchDecision})과 동형이지만,
 * 게이트1이 "매칭 판에 낄지"라면 게이트2는 "이 상대와 대화할지"라 의미가 달라 별도 enum으로 둔다.
 *
 * <p>양쪽 유저가 모두 ACCEPTED 되는 순간 매칭은 연결(connected)되고, F5가 그 신호로 채팅방을 연다.
 * 한 명이라도 REJECTED 면 그날 매칭은 종료된다(둘 다 소진).
 */
public enum Gate2Decision {

    /** 아직 채팅 수락/거부를 정하지 않음. FE는 "매칭 발견(상대 공개)" 화면에서 선택을 받는다. */
    PENDING,

    /** 채팅 수락. 상대도 수락하면 연결된다. */
    ACCEPTED,

    /** 채팅 거부. 그날 매칭은 종료된다. */
    REJECTED
}
