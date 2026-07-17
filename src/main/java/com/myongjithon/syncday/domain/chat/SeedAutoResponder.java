package com.myongjithon.syncday.domain.chat;

import com.myongjithon.syncday.domain.match.Match;
import com.myongjithon.syncday.domain.match.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 데모 전용 — 시드 계정 자동 응답. 봇이 아니라 자동 응답 1회로 한정한다(개정안 규약).
 * 라이브 유저의 첫 메시지에 대해 시드 계정 명의로 고정 문구 1건을 짧은 지연 후 insert 하고,
 * 이후에는 응답하지 않는다. 추가 대화가 필요하면 방식 B(시드 userId 로 채팅 API 직접 호출)로 대응한다.
 *
 * @Async 라 호출자 트랜잭션과 분리된다 — 지연(sleep)이 라이브 유저의 전송 요청을 붙잡지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedAutoResponder {

    private static final long REPLY_DELAY_MS = 2500L;
    private static final String FIXED_REPLY = "안녕하세요! 오늘 하루 정말 비슷했네요 😊";

    private final MatchRepository matchRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replyLater(UUID matchId, UUID seedUserId) {
        try {
            Thread.sleep(REPLY_DELAY_MS);
            Match match = matchRepository.findById(matchId).orElse(null);
            if (match == null) {
                return;
            }
            // 지연 사이에 이미 답장이 생겼으면(중복 트리거·방식 B 수동 개입) 아무것도 안 한다.
            if (chatMessageRepository.existsByMatch_MatchIdAndSenderId(matchId, seedUserId)) {
                return;
            }
            chatMessageRepository.save(ChatMessage.of(match, seedUserId, FIXED_REPLY));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 자동 응답 실패가 라이브 유저 플로우를 깨면 안 된다 — 로그만 남긴다.
            log.warn("시드 자동 응답 실패 matchId={}", matchId, e);
        }
    }
}