package com.project.back.notification.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRepositoryTest {

    private final SseEmitterRepository repository = new SseEmitterRepository();

    @Test
    void add_후_get으로_조회된다() {
        SseEmitter emitter = new SseEmitter();
        repository.add(1L, emitter);

        assertThat(repository.get(1L)).containsExactly(emitter);
    }

    @Test
    void 한_사용자가_여러_연결을_가질_수_있다() {
        SseEmitter e1 = new SseEmitter();
        SseEmitter e2 = new SseEmitter();
        repository.add(1L, e1);
        repository.add(1L, e2);

        assertThat(repository.get(1L)).containsExactlyInAnyOrder(e1, e2);
    }

    @Test
    void 연결이_없는_사용자는_빈_리스트를_반환한다() {
        assertThat(repository.get(999L)).isEmpty();
    }

    @Test
    void remove하면_해당_연결만_제거된다() {
        SseEmitter e1 = new SseEmitter();
        SseEmitter e2 = new SseEmitter();
        repository.add(1L, e1);
        repository.add(1L, e2);

        repository.remove(1L, e1);

        assertThat(repository.get(1L)).containsExactly(e2);
    }

    @Test
    void 마지막_연결이_제거되면_사용자_엔트리도_비워진다() {
        SseEmitter emitter = new SseEmitter();
        repository.add(1L, emitter);

        repository.remove(1L, emitter);

        assertThat(repository.get(1L)).isEmpty();
    }

    @Test
    void getAllEntries는_모든_사용자의_연결을_userId와_함께_평탄화해서_반환한다() {
        SseEmitter e1 = new SseEmitter();
        SseEmitter e2 = new SseEmitter();
        SseEmitter e3 = new SseEmitter();
        repository.add(1L, e1);
        repository.add(1L, e2);
        repository.add(2L, e3);

        assertThat(repository.getAllEntries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, e1),
                        org.assertj.core.groups.Tuple.tuple(1L, e2),
                        org.assertj.core.groups.Tuple.tuple(2L, e3)
                );
    }

    @Test
    void getAllEntries는_연결이_없으면_빈_리스트를_반환한다() {
        assertThat(repository.getAllEntries()).isEmpty();
    }
}
