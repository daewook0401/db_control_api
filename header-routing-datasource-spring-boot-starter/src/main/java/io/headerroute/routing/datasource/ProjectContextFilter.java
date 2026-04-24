package io.headerroute.routing.datasource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

public class ProjectContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ProjectContextFilter.class);

    private final ProjectRoutingProperties properties;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public ProjectContextFilter(
            ProjectRoutingProperties properties,
            HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.properties = properties;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String headerName = properties.getHeaderName();
            String project = request.getHeader(headerName);
            validateProject(project, headerName, properties.getProjects().keySet());

            ProjectContext.set(project);
            log.debug("Resolved project header. uri={}, header={}, project={}",
                    request.getRequestURI(), headerName, project);
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            handlerExceptionResolver.resolveException(request, response, null, exception);
        } finally {
            log.debug("Clearing project context. uri={}, project={}",
                    request.getRequestURI(), ProjectContext.get());
            ProjectContext.clear();
        }
    }

    private void validateProject(String project, String headerName, Set<String> allowedProjects) {
        if (project == null || project.isBlank()) {
            throw new ProjectHeaderMissingException("Missing required header: " + headerName);
        }
        if (properties.isStrict() && !allowedProjects.contains(project)) {
            throw new ProjectAccessDeniedException("Forbidden project header: " + project);
        }
    }
}
