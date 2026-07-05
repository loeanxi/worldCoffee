module/ai/
controller/
AiController.java         ← 薄的，只处理请求/响应
service/
AiService.java            ← 厚的，所有业务逻辑
config/
SpringAIConfig.java       ← 配置类
tool/
ProductTool.java          ← 商品搜索工具
PostTool.java             ← 帖子搜索工具
entity/
AiConversation.java       ← 会话实体
dao/
AiConversationDao.java    ← 数据访问
Prompts.java                ← 提示词常量