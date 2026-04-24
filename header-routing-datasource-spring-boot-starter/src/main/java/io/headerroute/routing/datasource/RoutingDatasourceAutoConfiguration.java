package io.headerroute.routing.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.HandlerExceptionResolver;

@AutoConfiguration
@EnableConfigurationProperties(ProjectRoutingProperties.class)
public class RoutingDatasourceAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "routingDataSource")
    public DataSource routingDataSource(ProjectRoutingProperties properties) {
        if (properties.getProjects().isEmpty()) {
            throw new IllegalStateException("No routing datasource projects configured under app.routing.datasource.projects");
        }

        ProjectRoutingDataSource routingDataSource = new ProjectRoutingDataSource();
        Map<Object, Object> targetDataSources = new LinkedHashMap<>();
        properties.getProjects().forEach((project, config) ->
                targetDataSources.put(project, buildDataSource(project, config)));

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setLenientFallback(false);
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public ProjectContextFilter projectContextFilter(
            ProjectRoutingProperties properties,
            HandlerExceptionResolver handlerExceptionResolver
    ) {
        return new ProjectContextFilter(properties, handlerExceptionResolver);
    }

    private DataSource buildDataSource(
            String project,
            ProjectRoutingProperties.ProjectDataSourceProperties properties
    ) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(properties.getJdbcUrl());
        hikariConfig.setUsername(properties.getUsername());
        hikariConfig.setPassword(properties.getPassword());
        hikariConfig.setDriverClassName(properties.getDriverClassName());
        hikariConfig.setPoolName("project-" + project + "-pool");
        return new HikariDataSource(hikariConfig);
    }
}
