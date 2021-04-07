package com.github.apigcc.core;

import com.github.apigcc.core.common.helper.FileHelper;
import com.github.apigcc.core.declaration.CodeTypeDeclaration;
import com.github.apigcc.core.render.AsciidocRender;
import com.github.apigcc.core.render.PostmanRender;
import com.github.apigcc.core.render.ProjectRender;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

@Slf4j
@Getter
public class Context {

    public static final Integer DEFAULT_NODE_INDEX = 99;
    public static final String DEFAULT_PROJECT_ID = "api";
    public static final String DEFAULT_BUILD_PATH = "build";
    public static final String DEFAULT_DEPENDENCY_CODE_PATH = "dependency";
    public static final String DEFAULT_CODE_STRUCTURE = "src/main/java";

    /**
     * 设置当前解析框架
     */
    @Setter
    public String framework;

    @Setter
    public List<ProjectRender> renders = Lists.newArrayList(
            new AsciidocRender(),
            new PostmanRender());

    @Setter
    private Path buildPath = Paths.get(DEFAULT_BUILD_PATH);

    @Setter
    private Path dependencyPath = Paths.get(DEFAULT_DEPENDENCY_CODE_PATH);

    /**
     * 源码目录
     */
    private List<Path> sources = Lists.newArrayList();

    /**
     * 依赖源码
     */
    private List<Path> dependencies = Lists.newArrayList();

    /**
     * 代码声明
     */
    @Getter
    private List<CodeTypeDeclaration> codeTypeDeclarations = Lists.newArrayList();

    /**
     * 依赖jar包
     */
    private List<Path> jars = Lists.newArrayList();

    @Setter
    private String id = DEFAULT_PROJECT_ID;
    @Setter
    private String name;
    @Setter
    private String description;
    @Setter
    private String version;

    /**
     * 渲染html时的css
     */
    @Setter
    private String css;
    /**
     * 生成的链接前缀prefix
     */
    @Setter
    private String urlPrefix;
    /**
     * 要排除url的匹配
     */
    private String urlExcludeYmlPath;
    private List<String> urlExcludeList;
    /**
     * 只生成某些url的匹配
     */
    private String urlOnlyYmlPath;
    private List<String> urlOnlyList;

    public void addSource(Path path){
        sources.add(path);
//        sources.addAll(FileHelper.find(path, DEFAULT_CODE_STRUCTURE));
        // 只处理controller子目录
        sources.addAll(FileHelper.find(path.resolve(DEFAULT_CODE_STRUCTURE), "controller"));
        addDependency(path);
    }

    public void addDependency(Path path){
        dependencies.add(path);
        dependencies.addAll(FileHelper.find(path, DEFAULT_CODE_STRUCTURE));
    }

    public void addJar(Path path){
        jars.add(path);
    }

    /**
     * 是否存在声明
     * @param id
     * @return
     */
    public boolean hasCodeTypeDeclaration(String id){
        for (CodeTypeDeclaration codeTypeDeclaration : codeTypeDeclarations) {
            if(Objects.equals(codeTypeDeclaration.id(), id)){
                return true;
            }
        }
        return false;
    }

    /**
     * 构建TypeSolver
     * @return
     */
    public TypeSolver buildTypeSolver(){
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        for (Path dependency : dependencies) {
            typeSolver.add(new JavaParserTypeSolver(dependency));
        }
        for (Path jar : jars) {
            try {
                typeSolver.add(new JarTypeSolver(jar));
            } catch (IOException e) {
                log.warn("exception on {} {}", jar, e.getMessage());
            }
        }
        if (!codeTypeDeclarations.isEmpty()) {
            for (CodeTypeDeclaration codeTypeDeclaration : codeTypeDeclarations) {
                Path path = dependencyPath.resolve(codeTypeDeclaration.path());
                FileHelper.write(path, codeTypeDeclaration.code());
            }
            typeSolver.add(new JavaParserTypeSolver(dependencyPath));
        }
        typeSolver.add(new ReflectionTypeSolver());
        return typeSolver;
    }

    public void setUrlExcludeYmlPath(String urlExcludeYmlPath) {
        this.urlExcludeYmlPath = urlExcludeYmlPath;
        Yaml yaml = new Yaml();
        if (urlExcludeYmlPath != null && urlExcludeYmlPath.length() > 0) {
            try {
                this.urlExcludeList = yaml.load(new FileInputStream(urlExcludeYmlPath));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void setUrlOnlyYmlPath(String urlOnlyYmlPath) {
        this.urlOnlyYmlPath = urlOnlyYmlPath;
        Yaml yaml = new Yaml();
        if (urlOnlyYmlPath != null && urlOnlyYmlPath.length() > 0) {
            try {
                this.urlOnlyList = yaml.load(new FileInputStream(urlOnlyYmlPath));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
