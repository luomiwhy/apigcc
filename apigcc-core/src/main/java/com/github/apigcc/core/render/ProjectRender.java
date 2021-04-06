package com.github.apigcc.core.render;

import com.github.apigcc.core.Apigcc;
import com.github.apigcc.core.schema.Project;
import com.github.apigcc.core.schema.Section;

import java.util.List;

public interface ProjectRender {

    void render(Project project);

    /**
     * 有only只以only为准，否则以exclude为准
     */
    default boolean shouldSkipRender(Section section){
        List<String> list = Apigcc.getInstance().getContext().getUrlOnlyList();
        if (list.isEmpty()) {
            return Apigcc.getInstance().getContext().getUrlExcludeList().contains(section.getUri());
        }else {
            return !list.contains(section.getUri());
        }

    }
}
