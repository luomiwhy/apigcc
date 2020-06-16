package com.github.apigcc.springmvc;

import com.github.apigcc.core.Apigcc;
import com.github.apigcc.core.common.URI;
import com.github.apigcc.core.description.ObjectTypeDescription;
import com.github.apigcc.core.description.TypeDescription;
import com.github.apigcc.core.common.helper.AnnotationHelper;
import com.github.apigcc.core.common.helper.ExpressionHelper;
import com.github.apigcc.core.common.helper.StringHelper;
import com.github.apigcc.core.parser.ParserStrategy;
import com.github.apigcc.core.schema.*;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Optional;

public class SpringParser implements ParserStrategy {

    public static final String FRAMEWORK = "springmvc";

    public static final String ANNOTATION_CONTROLLER = "Controller";
    public static final String ANNOTATION_REST_CONTROLLER = "RestController";

    public static final String EXT_URI = "uri";

    public static final List<String> ANNOTATION_CONTROLLERS = Lists.newArrayList(ANNOTATION_CONTROLLER, ANNOTATION_REST_CONTROLLER);

    @Override
    public String name() {
        return FRAMEWORK;
    }

    @Override
    public void onLoad() {
        Apigcc.getInstance().getTypeResolvers().addResolver(new SpringComponentResolver());
        Apigcc.getInstance().getTypeResolvers().addNameResolver(new SpringComponentResolver());
//        Apigcc.getInstance().getContext().getCodeTypeDeclarations().add(new PageTypeDeclaration());
    }

    /**
     * 处理被@RestController和@Controller标记的类
     *
     * @param n
     * @return
     */
    @Override
    public boolean accept(ClassOrInterfaceDeclaration n) {
        return AnnotationHelper.any(n, ANNOTATION_CONTROLLERS);
    }

    /**
     * 类被@RestController标记，或方法被@ResponseBody标记
     *
     * @param n
     * @return
     */
    @Override
    public boolean accept(MethodDeclaration n) {
        return RequestMappingHelper.isRest(n) && AnnotationHelper.any(n, RequestMappingHelper.ANNOTATION_REQUEST_MAPPINGS);
    }

    /**
     * 解析类定义
     *
     * @param n
     * @param chapter
     */
    @Override
    public void visit(ClassOrInterfaceDeclaration n, Chapter chapter) {
        chapter.getExt().put(EXT_URI, RequestMappingHelper.pickUriToParent(n).toString());
    }

    /**
     * 解析方法定义
     *
     * @param n
     * @param chapter
     * @param section
     */
    @Override
    public void visit(MethodDeclaration n, Chapter chapter, Section section) {
        visitMethod(n, section);
        visitUri(n, chapter, section);
        visitPathVariable(n, section);
        visitHeaders(n, section);
        visitParameters(n, section);
    }

    /**
     * 解析请求方法
     *
     * @param n
     * @param section
     */
    private void visitMethod(MethodDeclaration n, Section section) {
        section.setMethod(RequestMappingHelper.pickMethod(n));
    }

    /**
     * 解析请求URI，与父类URI拼接
     *
     * @param n
     * @param chapter
     * @param section
     */
    private void visitUri(MethodDeclaration n, Chapter chapter, Section section) {
        URI uri = new URI((String) chapter.getExt().get(EXT_URI));
        uri.add(RequestMappingHelper.pickUri(n.getAnnotations()));
        section.setUri(uri.toString());
    }

    /**
     * 解析方法参数
     *
     * @param n
     * @param section
     */
    private void visitParameters(MethodDeclaration n, Section section) {
        if (Method.GET.equals(section.getMethod())) {
            visitParameter(n, section);
        } else {
            visitRequestBody(n, section);
        }
    }

    /**
     * 解析PathVariable
     *
     * @param n
     * @param section
     */
    private void visitPathVariable(MethodDeclaration n, Section section) {
        for (Parameter parameter : n.getParameters()) {
            if (ParameterHelper.isPathVariable(parameter)) {
                section.getPathVariable().put(parameter.getNameAsString(), "");
                Row row = new Row();
                row.setKey(parameter.getNameAsString());
                row.setType(parameter.getType().toString());
                section.param(row.getKey()).ifPresent(tag -> row.setRemark(tag.getContent()));
                section.addRequestRow(row);
            }
        }
    }

    /**
     * 解析RequestHeader
     *
     * @param n
     * @param section
     */
    private void visitHeaders(MethodDeclaration n, Section section) {

        List<String> headers = RequestMappingHelper.pickHeaders(n.getAnnotations());
        for (String text : headers) {
            section.addInHeader(Header.valueOf(text));
        }

        List<String> consumers = RequestMappingHelper.pickConsumers(n.getAnnotations());
        if (!consumers.isEmpty()) {
            section.addInHeader(new Header("Content-Type", String.join(",", consumers)));
        }

        List<String> produces = RequestMappingHelper.pickProduces(n.getAnnotations());
        if (!produces.isEmpty()) {
            section.addOutHeader(new Header("Content-Type", String.join(",", produces)));
        }

        for (Parameter parameter : n.getParameters()) {
            if (ParameterHelper.isRequestHeader(parameter)) {
                String key = parameter.getNameAsString();
                String defaultValue = "{value}";
                AnnotationExpr annotationExpr = parameter.getAnnotationByName(ParameterHelper.ANNOTATION_REQUEST_HEADER).get();
                Optional<Expression> valueOptional = AnnotationHelper.attr(annotationExpr, "value", "name");
                if (valueOptional.isPresent()) {
                    key = String.valueOf(ExpressionHelper.getValue(valueOptional.get()));
                }
                Optional<Expression> defaultValueOptional = AnnotationHelper.attr(annotationExpr, "defaultValue");
                if (defaultValueOptional.isPresent()) {
                    defaultValue = String.valueOf(ExpressionHelper.getValue(defaultValueOptional.get()));
                }
                TypeDescription description = Apigcc.getInstance().getTypeResolvers().resolve(parameter.getType());
                if (description.isAvailable()) {
                    Object value = description.getValue();
                    if (StringHelper.isBlank(defaultValue) && StringHelper.nonBlank(value)) {
                        defaultValue = String.valueOf(value);
                    }
                    section.addInHeader(new Header(key, defaultValue));
                }
            }
        }
    }

    /**
     * 解析RequestBody
     *
     * @param n
     * @param section
     */
    private void visitRequestBody(MethodDeclaration n, Section section) {
        section.setQueryParameter(false);
        section.addInHeader(Header.APPLICATION_JSON);

        if (ParameterHelper.hasRequestBody(n.getParameters())) {
            Parameter parameter = ParameterHelper.getRequestBody(n.getParameters());
            TypeDescription description = Apigcc.getInstance().getTypeResolvers().resolve(parameter.getType());
            if (description.isAvailable()) {
                if (description.isArray()) {
                    section.setParameter(description.asArray().getValue());
                } else if (description.isObject()) {
                    section.setParameter(description.asObject().getValue());
                }
                section.addRequestRows(description.rows());
            }
        } else {
            ObjectTypeDescription objectTypeDescription = new ObjectTypeDescription();
            for (Parameter parameter : n.getParameters()) {
                if (ParameterHelper.isRequestParam(parameter)) {
                    TypeDescription description = Apigcc.getInstance().getTypeResolvers().resolve(parameter.getType());
                    if (description.isAvailable()) {
                        if (description.isObject()) {
                            objectTypeDescription.merge(description.asObject());
                        } else {
                            String key = parameter.getNameAsString();
                            description.setKey(key);
                            section.param(key).ifPresent(tag -> description.addRemark(tag.getContent()));
                            objectTypeDescription.add(description);
                        }
                    }
                }
            }
            section.setParameter(objectTypeDescription.getValue());
            section.addRequestRows(objectTypeDescription.rows());
        }

    }

    /**
     * 解析RequestParameter
     *
     * @param n
     * @param section
     */
    private void visitParameter(MethodDeclaration n, Section section) {
        ObjectTypeDescription objectTypeDescription = new ObjectTypeDescription();
        for (Parameter parameter : n.getParameters()) {
            if (ParameterHelper.isRequestParam(parameter)) {
                String key = parameter.getNameAsString();

                Object defaultValue = null;
                Boolean required = null;

                Optional<AnnotationExpr> optional = parameter.getAnnotationByName(ParameterHelper.ANNOTATION_REQUEST_PARAM);
                if (optional.isPresent()) {
                    Optional<Expression> valueOptional = AnnotationHelper.attr(optional.get(), "value", "name");
                    if (valueOptional.isPresent()) {
                        key = String.valueOf(ExpressionHelper.getValue(valueOptional.get()));
                    }
                    Optional<Expression> defaultValueOptional = AnnotationHelper.attr(optional.get(), "defaultValue");
                    if (defaultValueOptional.isPresent()) {
                        defaultValue = ExpressionHelper.getValue(defaultValueOptional.get());
                    }
                    Optional<Expression> requiredOptional = AnnotationHelper.attr(optional.get(), "required");
                    if (requiredOptional.isPresent() && requiredOptional.get().isBooleanLiteralExpr()) {
                        required = requiredOptional.get().asBooleanLiteralExpr().getValue();
                    }
                }

                TypeDescription description = Apigcc.getInstance().getTypeResolvers().resolve(parameter.getType());
                if (description.isAvailable()) {
                    section.param(key).ifPresent(tag -> description.addRemark(tag.getContent()));
                    if (required != null) {
                        description.setRequired(required);
                    }
                    if (description.isObject()) {
                        objectTypeDescription.merge(description.asObject());
                    } else {
                        description.setKey(key);
                        if (defaultValue != null && (description.isPrimitive() || description.isString())) {
                            description.setDefaultValue(defaultValue);
                        }
                        objectTypeDescription.add(description);
                    }
                }
            }
        }
        section.setParameter(objectTypeDescription.getValue());
        section.addRequestRows(objectTypeDescription.rows());
    }

}
