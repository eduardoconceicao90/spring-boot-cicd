package com.example.cicd.service;

import com.example.cicd.dto.TaskRequest;
import com.example.cicd.dto.TaskResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TaskService {

    private final Map<Long, TaskResponse> tasks = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    private final Counter tasksCreatedCounter;
    private final Counter tasksDeletedCounter;

    public TaskService(MeterRegistry meterRegistry) {
        this.tasksCreatedCounter = Counter.builder("tasks_created_total")
                .description("Total number of tasks created")
                .register(meterRegistry);

        this.tasksDeletedCounter = Counter.builder("tasks_deleted_total")
                .description("Total number of tasks deleted")
                .register(meterRegistry);

        meterRegistry.gauge("tasks_active_count", tasks, Map::size);
    }

    public List<TaskResponse> findAll() {
        return List.copyOf(tasks.values());
    }

    public Optional<TaskResponse> findById(Long id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public TaskResponse create(TaskRequest request) {
        Long id = sequence.incrementAndGet();
        TaskResponse task = new TaskResponse(id, request.title(), request.completed());
        tasks.put(id, task);
        tasksCreatedCounter.increment();
        return task;
    }

    public Optional<TaskResponse> update(Long id, TaskRequest request) {
        if (!tasks.containsKey(id)) {
            return Optional.empty();
        }
        TaskResponse updated = new TaskResponse(id, request.title(), request.completed());
        tasks.put(id, updated);
        return Optional.of(updated);
    }

    public void delete(Long id) {
        if (tasks.remove(id) != null) {
            tasksDeletedCounter.increment();
        }
    }
}
