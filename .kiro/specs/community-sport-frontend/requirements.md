# Requirements Document

## Introduction

社区运动场地管理系统前端开发需求文档。本系统为社区居民提供运动场地预约、体育器材购买、教练课程预约等服务，同时为员工和管理员提供后台管理功能。前端采用 Vue 3 + Naive UI + Pinia + Vue Router 技术栈，与已完成的 Spring Boot 后端 API 对接。

## Glossary

- **Frontend**: 基于 Vue 3 的单页应用程序
- **User_Portal**: 面向普通用户的前台界面
- **Admin_Portal**: 面向管理员和员工的后台管理界面
- **Auth_Module**: 认证模块，处理登录、注册、Token 管理
- **Venue_Module**: 场地模块，处理场地浏览、时段查询、预约
- **Equipment_Module**: 器材模块，处理器材浏览、购物车、订单
- **Course_Module**: 课程模块，处理课程浏览、预约、评价
- **Wallet_Module**: 钱包模块，处理余额查询、充值申请、签到
- **Coach_Portal**: 教练端界面
- **Staff_Portal**: 员工端界面

## Requirements

### Requirement 1: 用户认证与授权

**User Story:** As a user, I want to register, login, and manage my session, so that I can access the system securely.

#### Acceptance Criteria

1. WHEN a user visits the login page, THE Auth_Module SHALL display a login form with username and password fields
2. WHEN a user submits valid credentials, THE Auth_Module SHALL store the token pair and redirect to the home page
3. WHEN a user submits invalid credentials, THE Auth_Module SHALL display an error message
4. WHEN a user clicks register, THE Auth_Module SHALL display a registration form with required fields
5. WHEN a user submits valid registration data, THE Auth_Module SHALL create the account and redirect to login
6. WHEN a user clicks logout, THE Auth_Module SHALL clear tokens and redirect to login page
7. WHEN an access token expires, THE Auth_Module SHALL automatically refresh using the refresh token
8. IF the refresh token is invalid, THEN THE Auth_Module SHALL redirect to login page
9. WHEN a user clicks "forgot password", THE Auth_Module SHALL display password reset form with email verification

### Requirement 2: 系统首页与推荐

**User Story:** As a user, I want to see recommended content on the home page, so that I can quickly discover popular venues, courses, and equipment.

#### Acceptance Criteria

1. WHEN a user visits the home page, THE User_Portal SHALL display banner carousel images
2. WHEN a user visits the home page, THE User_Portal SHALL display hot venue recommendations
3. WHEN a user visits the home page, THE User_Portal SHALL display quality course recommendations
4. WHEN a user visits the home page, THE User_Portal SHALL display equipment promotions
5. WHEN a user visits the home page, THE User_Portal SHALL display community announcements
6. WHEN a user clicks a recommendation item, THE User_Portal SHALL navigate to the detail page

### Requirement 3: 运动场地预约

**User Story:** As a user, I want to browse and book sports venues, so that I can reserve time slots for my activities.

#### Acceptance Criteria

1. WHEN a user visits the venue list page, THE Venue_Module SHALL display all available venues with filtering options
2. WHEN a user selects a venue type filter, THE Venue_Module SHALL filter venues by the selected type
3. WHEN a user clicks a venue, THE Venue_Module SHALL display venue details including location, type, specifications, hours, and pricing
4. WHEN a user selects a date, THE Venue_Module SHALL display available time slots for that date
5. WHEN a user selects a time slot and confirms booking, THE Venue_Module SHALL deduct wallet balance and create the booking
6. IF wallet balance is insufficient, THEN THE Venue_Module SHALL display an error and prevent booking
7. WHEN a booking is successful, THE Venue_Module SHALL display a booking confirmation with verification code
8. WHEN a user views their bookings, THE Venue_Module SHALL display booking history with status
9. WHEN a user cancels a booking before the start time, THE Venue_Module SHALL process refund according to rules

### Requirement 4: 体育器材购买

**User Story:** As a user, I want to browse and purchase sports equipment, so that I can buy items I need.

#### Acceptance Criteria

1. WHEN a user visits the equipment list page, THE Equipment_Module SHALL display equipment with categories and search
2. WHEN a user clicks an equipment item, THE Equipment_Module SHALL display details including name, specs, price, stock, and reviews
3. WHEN a user adds an item to cart, THE Equipment_Module SHALL update the cart with the item
4. WHEN a user views the cart, THE Equipment_Module SHALL display all cart items with quantities and total price
5. WHEN a user proceeds to checkout, THE Equipment_Module SHALL display address selection and order summary
6. WHEN a user confirms the order, THE Equipment_Module SHALL deduct wallet balance and create the order
7. IF wallet balance is insufficient, THEN THE Equipment_Module SHALL display an error and prevent order creation
8. WHEN a user views their orders, THE Equipment_Module SHALL display order history with status and tracking
9. WHEN a user confirms receipt, THE Equipment_Module SHALL update order status to received
10. WHEN a user submits a review, THE Equipment_Module SHALL save the review for the equipment

### Requirement 5: 教练课程预约

**User Story:** As a user, I want to browse and book coach courses, so that I can learn from professional coaches.

#### Acceptance Criteria

1. WHEN a user visits the course list page, THE Course_Module SHALL display available courses with filtering
2. WHEN a user clicks a course, THE Course_Module SHALL display course details including coach info, duration, price, venue, and capacity
3. WHEN a user views course sessions, THE Course_Module SHALL display available session times
4. WHEN a user books a session, THE Course_Module SHALL create a booking pending coach approval
5. WHEN a user pays for an approved booking, THE Course_Module SHALL deduct wallet balance and confirm the booking
6. WHEN a user views their course bookings, THE Course_Module SHALL display booking history with status
7. WHEN a user cancels a booking, THE Course_Module SHALL process refund according to rules
8. WHEN a user submits a course review, THE Course_Module SHALL save the review for the course

### Requirement 6: 用户投诉

**User Story:** As a user, I want to submit and track complaints, so that I can report issues and get resolution.

#### Acceptance Criteria

1. WHEN a user clicks submit complaint, THE User_Portal SHALL display a complaint form with type selection
2. WHEN a user submits a complaint, THE User_Portal SHALL create the complaint and display confirmation
3. WHEN a user views their complaints, THE User_Portal SHALL display complaint history with status
4. WHEN a user views a complaint detail, THE User_Portal SHALL display messages and processing progress
5. WHEN a user adds a follow-up message, THE User_Portal SHALL append the message to the complaint

### Requirement 7: 个人中心

**User Story:** As a user, I want to manage my profile and view my records, so that I can maintain my account.

#### Acceptance Criteria

1. WHEN a user visits the profile page, THE User_Portal SHALL display user information with edit options
2. WHEN a user updates profile info, THE User_Portal SHALL save the changes
3. WHEN a user uploads an avatar, THE User_Portal SHALL update the avatar image
4. WHEN a user changes password, THE User_Portal SHALL validate and update the password
5. WHEN a user views addresses, THE User_Portal SHALL display address list with CRUD operations
6. WHEN a user views wallet, THE User_Portal SHALL display balance and transaction history
7. WHEN a user submits a top-up request, THE Wallet_Module SHALL create the request pending admin approval
8. WHEN a user performs daily sign-in, THE Wallet_Module SHALL award points according to rules
9. WHEN a user views favorites, THE User_Portal SHALL display favorited items by category
10. WHEN a user views messages, THE User_Portal SHALL display notifications with read/unread status

### Requirement 8: 教练端功能

**User Story:** As a coach, I want to manage my courses and handle bookings, so that I can provide coaching services.

#### Acceptance Criteria

1. WHEN a user applies for coach certification, THE Coach_Portal SHALL submit the application with credentials
2. WHEN a coach creates a course, THE Coach_Portal SHALL save the course with details and sessions
3. WHEN a coach views bookings, THE Coach_Portal SHALL display pending and confirmed bookings
4. WHEN a coach accepts or rejects a booking, THE Coach_Portal SHALL update the booking status
5. WHEN a coach verifies attendance, THE Coach_Portal SHALL mark the booking as verified
6. WHEN a coach views earnings, THE Coach_Portal SHALL display income summary and withdrawal options
7. WHEN a coach submits a withdrawal request, THE Coach_Portal SHALL create the request pending admin approval
8. WHEN a coach views consultations, THE Coach_Portal SHALL display user inquiries with reply options

### Requirement 9: 员工端功能

**User Story:** As a staff member, I want to handle complaints and perform inspections, so that I can maintain service quality.

#### Acceptance Criteria

1. WHEN a staff views assigned complaints, THE Staff_Portal SHALL display complaints with processing options
2. WHEN a staff updates complaint status, THE Staff_Portal SHALL save the status change
3. WHEN a staff adds a complaint message, THE Staff_Portal SHALL append the message
4. WHEN a staff performs venue verification, THE Staff_Portal SHALL record the verification result
5. WHEN a staff submits an inspection report, THE Staff_Portal SHALL create the inspection record
6. WHEN a staff views daily report, THE Staff_Portal SHALL display summary statistics

### Requirement 10: 管理员端功能

**User Story:** As an admin, I want to manage users, resources, and system settings, so that I can administer the platform.

#### Acceptance Criteria

1. WHEN an admin views users, THE Admin_Portal SHALL display user list with search and filter
2. WHEN an admin creates or edits a user, THE Admin_Portal SHALL save the user data
3. WHEN an admin updates user status, THE Admin_Portal SHALL enable or disable the account
4. WHEN an admin reviews coach applications, THE Admin_Portal SHALL approve or reject with reason
5. WHEN an admin manages venues, THE Admin_Portal SHALL provide CRUD operations for venues
6. WHEN an admin manages equipment, THE Admin_Portal SHALL provide CRUD operations for equipment
7. WHEN an admin processes equipment orders, THE Admin_Portal SHALL update shipping information
8. WHEN an admin reviews top-up requests, THE Admin_Portal SHALL approve or reject requests
9. WHEN an admin reviews withdrawal requests, THE Admin_Portal SHALL approve or reject requests
10. WHEN an admin manages announcements, THE Admin_Portal SHALL provide CRUD operations for notices
11. WHEN an admin manages banners, THE Admin_Portal SHALL provide CRUD operations for banners
12. WHEN an admin views metrics, THE Admin_Portal SHALL display system statistics and reports
13. WHEN an admin configures system parameters, THE Admin_Portal SHALL save configuration changes

### Requirement 11: 教学视频功能

**User Story:** As a user, I want to browse and purchase coaching videos, so that I can learn at my own pace.

#### Acceptance Criteria

1. WHEN a user visits the video list page, THE User_Portal SHALL display available videos with filtering
2. WHEN a user clicks a video, THE User_Portal SHALL display video details and preview
3. WHEN a user purchases a video, THE User_Portal SHALL deduct wallet balance and grant access
4. WHEN a user views purchased videos, THE User_Portal SHALL display the video player
5. WHEN a coach uploads a video, THE Coach_Portal SHALL save the video with metadata
6. WHEN a coach manages videos, THE Coach_Portal SHALL provide edit and status control options
