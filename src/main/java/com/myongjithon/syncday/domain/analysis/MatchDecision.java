package com.myongjithon.syncday.domain.analysis;

/**
 * 유저가 그날 매칭에 참여할지(F3 게이트1) 정한 결정. 하루 분석(AnalysisResult) 1건당 하나다.
 *
 * <p>매칭 후보는 "분석을 끝낸 반대 캠퍼스 유저 전원"이 아니라 <b>ACCEPTED 한 유저만</b>이다.
 * 즉 opt-in: 유저가 명시적으로 수락해야 매칭 대상이 되고, 거부하거나 아직 정하지 않은 유저는 상대로 잡히지 않는다.
 */
public enum MatchDecision {

    /** 분석은 끝났지만 아직 매칭 수락/거부를 정하지 않음. FE는 "매칭 수락/거부" 화면을 띄운다. */
    NONE,

    /** 매칭 수락. 이 유저는 매칭 후보 풀에 포함된다. */
    ACCEPTED,

    /** 매칭 거부. 후보 풀에서 제외된다(남의 상대로도 잡히지 않음). */
    DECLINED
}
