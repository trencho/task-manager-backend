package com.project.taskmanager.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.project.taskmanager.entity.Task;
import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            final LocalDate dueBefore, final String tag, final Pageable pageable) {
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
        if (StringUtils.hasText(tag)) {
            // Exact match, not a regex: a tag is a label the user chose from their own set, and
            // substring matching would make "work" also select "homework".
            criteria.add(Criteria.where("tags").is(tag));
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

    @Override
    public List<Task> findDueReminders(final String username, final LocalDate dueOnOrBefore) {
        final var query = new Query(new Criteria().andOperator(
                // Always scoped to the owner. Never make this conditional.
                Criteria.where("username").is(username),
                // `ne` rather than `in(PENDING, IN_PROGRESS)`: a status added later is a reminder
                // candidate by default, which is the safe direction -- the alternative silently drops
                // it from every reminder list until someone remembers to extend the enumeration.
                Criteria.where("status").ne(TaskStatus.COMPLETED),
                // A task with no deadline has nothing to be late for. `lte`, not `lt`: a task due on
                // the boundary day is due, and excluding it would skip the reminder on the one day it
                // matters most.
                Criteria.where("dueDate").ne(null).lte(dueOnOrBefore)));
        query.with(Sort.by(Sort.Direction.ASC, "dueDate"));
        return mongoTemplate.find(query, Task.class);
    }
}
