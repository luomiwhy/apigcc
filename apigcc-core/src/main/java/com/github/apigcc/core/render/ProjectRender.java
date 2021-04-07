package com.github.apigcc.core.render;

import com.github.apigcc.core.Apigcc;
import com.github.apigcc.core.schema.Project;
import com.github.apigcc.core.schema.Section;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.List;

public interface ProjectRender {

    void render(Project project);

    /**
     * 有only只以only为准，否则以exclude为准
     */
    default boolean shouldRender(Section section) {
        List<PathPattern> list = Apigcc.getInstance().getExtConfig().getUrlOnlyPatternList();
        PathContainer parsePath = PathContainer.parsePath(section.getUri());
        if (list.isEmpty()) {
            return Apigcc.getInstance().getExtConfig().getUrlExcludePatternList().stream().noneMatch(pathPattern -> pathPattern.matches(parsePath));
        } else {
            return list.stream().anyMatch(pathPattern -> pathPattern.matches(parsePath));
        }

    }
}
