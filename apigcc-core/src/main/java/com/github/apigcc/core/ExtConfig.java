package com.github.apigcc.core;


import lombok.Getter;
import lombok.Setter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ExtConfig {
    private final PathPatternParser pathPatternParser = new PathPatternParser();

    {
        pathPatternParser.setMatchOptionalTrailingSeparator(true);
    }

    /**
     * adoc渲染html
     */
    @Setter
    private Boolean renderHtml;
    /**
     * 文档生成目录
     */
    @Setter
    private String buildPath;
    @Setter
    private List<String> jars;
    /**
     * 扫描的根目录
     */
    @Setter
    private String rootDir;
    /**
     * 目录扫描深度
     */
    @Setter
    private Integer maxDepth;

    @Setter
    private List<DirModule> modules;

    /**
     * 要排除url的匹配
     */
    private List<PathPattern> urlExcludePatternList = new ArrayList<>();
    private List<String> urlExclude;
    /**
     * 只生成某些url的匹配
     */
    private List<PathPattern> urlOnlyPatternList = new ArrayList<>();
    private List<String> urlOnly;

    public void setUrlOnly(List<String> urlOnly) {
        this.urlOnly = urlOnly;
        urlOnly.forEach(s -> this.urlOnlyPatternList.add(pathPatternParser.parse(s)));
    }

    public void setUrlExclude(List<String> urlExclude) {
        this.urlExclude = urlExclude;
        urlExclude.forEach(s -> this.urlExcludePatternList.add(pathPatternParser.parse(s)));
    }
}
