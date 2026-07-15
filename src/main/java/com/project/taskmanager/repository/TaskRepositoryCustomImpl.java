package com.project.taskmanager.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.regex.Pattern;

import com.project.taskmanager.entity.Task;
import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

/**
 * The class name must be the fragment interface name plus {@code Impl}; that is how Spring Data
 * discovers it. Renaming either half silently unwires the fragment.
 */
@RequiredArgsConstructor
public class TaskRepositoryCustomImpl implements TaskRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Task> search(final String username, final TaskStatus status, final Priority priority, final String q,
            final LocalDate dueBefore, final Pageable pageable) {
        final var criteria = new ArrayList<Criteria>();

        // Always scoped to the owner. Never make this conditional.
        criteria.add(Criteria.where("username").is(username));

        if (status != null) {
            criteria.add(Criteria.where("status").is(status));
        }
        if (priority != null) {
            criteria.add(Criteria.where("priority").is(priority));
        }
        if (dueBefore != null) {
            criteria.add(Criteria.where("dueDate").lt(dueBefore));
        }
        if (StringUtils.hasText(q)) {
            // Pattern.quote: `q` is user input going into a regex. Unquoted, `.*` would match
            // every task and a pathological pattern would hang the server (ReDoS).
            final var quoted = Pattern.quote(q);
            criteria.add(new Criteria().orOperator(Criteria.where("title").regex(quoted, "i"),
                    Criteria.where("description").regex(quoted, "i")));
        }

        // andOperator, not a chain of .and(...): a chained .and() after the $or above would
        // overwrite it rather than nest beside it.
        final var query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        // count on the unpaged query, find on the paged one.
        final var total = mongoTemplate.count(query, Task.class);
        final var content = mongoTemplate.find(query.with(pageable), Task.class);

        return PageableExecutionUtils.getPage(content, pageable, () -> total);
    }
}
