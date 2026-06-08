package com.eRez.tests.data;

import com.eRez.tests.database.document.NodeDocument;
import com.eRez.tests.database.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheDataTest {

    @Mock private NodeRepository nodeRepository;

    private CacheData cacheData;

    @BeforeEach
    void setUp() {
        cacheData = new CacheData(nodeRepository);
    }

    private NodeDocument node(String id) {
        NodeDocument d = new NodeDocument();
        d.setId(id);
        d.setName(id);
        d.setConnections(java.util.Map.of());
        return d;
    }

    @Test
    void refresh_replacesListContents() {
        when(nodeRepository.findAll())
                .thenReturn(List.of(node("a")))
                .thenReturn(List.of(node("b"), node("c")));

        cacheData.refresh();
        assertThat(cacheData.getNodes()).hasSize(1);

        cacheData.refresh();
        assertThat(cacheData.getNodes()).hasSize(2); // replaced, not appended
    }

    @Test
    void getNodes_returnedListIsUnmodifiable() {
        when(nodeRepository.findAll()).thenReturn(List.of(node("a")));
        cacheData.refresh();

        assertThatThrownBy(() -> cacheData.getNodes().add(node("b")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void concurrentReadsDuringRefresh_noExceptionsThrown() throws InterruptedException {
        when(nodeRepository.findAll()).thenAnswer(inv -> List.of(node("a"), node("b")));
        cacheData.refresh();

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            boolean writer = (i % 4 == 0);
            executor.submit(() -> {
                try {
                    start.await();
                    if (writer) {
                        cacheData.refresh();
                    } else {
                        cacheData.getNodes().forEach(NodeDocument::getName); // iterate while writers run
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(errors).isEmpty();
    }
}
