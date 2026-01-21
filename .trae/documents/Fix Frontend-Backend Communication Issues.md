## Root Cause Analysis

The frontend cannot access the backend functionality due to several issues:

1. **Missing CORS Configuration**: The backend SecurityConfig.java doesn't include any CORS configuration, preventing the frontend (running on port 5173) from accessing the backend (running on port 8081) due to browser CORS restrictions.

2. **URL Mismatch**: The frontend axios configuration has `baseURL: 'http://localhost:8081/api'`, while the backend has a context-path of `/api`, resulting in duplicate `/api` in requests (e.g., `http://localhost:8081/api/api/...`).

3. **Port Configuration Difference**: The backend is running on port 8081, but the application.yml file shows port 8080, indicating a potential mismatch in configuration.

## Solution Plan

### 1. Fix CORS Configuration in Backend
- Add proper CORS configuration to `SecurityConfig.java` to allow frontend access
- Configure allowed origins, methods, headers, and credentials

### 2. Fix Frontend Axios BaseURL
- Update `blog-frontend/src/utils/axios.ts` to use `baseURL: 'http://localhost:8081'` instead of `http://localhost:8081/api`
- This avoids duplicate `/api` in request URLs since the backend already has it as context-path

### 3. Verify and Update Backend Port Configuration
- Check the actual backend port and update `application.yml` if necessary to maintain consistency

### 4. Test API Endpoints
- Test all API endpoints from the frontend to ensure they're accessible
- Verify authentication/authorization mechanisms work correctly
- Ensure proper error handling is implemented

## Implementation Steps

1. **Update SecurityConfig.java** to add CORS configuration
2. **Update axios.ts** to fix the baseURL
3. **Verify backend port** and update application.yml if needed
4. **Test all API endpoints** from the frontend
5. **Document the fixes** and verify functionality

## Expected Outcome

After implementing these fixes, the frontend should be able to successfully access all backend API endpoints, including:
- Authentication endpoints (/auth/login, /auth/register)
- Article endpoints (/articles)
- Category endpoints (/categories)
- Tag endpoints (/tags)
- Tool endpoints (/tools)

All endpoints should work correctly with proper CORS headers, authentication, and error handling.