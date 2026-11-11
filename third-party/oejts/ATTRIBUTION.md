# OEJTS / OpenJung 题库来源与改动记录

核查日期：2026-10-29。用途：本项目非商业毕业设计演示。

- 原量表：Open Extended Jungian Type Scales 1.2；作者 Eric Jorgenson / Open Psychometrics。
- 原文及评分说明：https://openpsychometrics.org/tests/OJTS/development/OEJTS1.2.pdf
- 原开发页：https://openpsychometrics.org/tests/OEJTS/development/
- 使用的中英文适配：OpenJung contributors，https://github.com/openjung/core
- 固定提交：`4d7917df1857e2dc04adc47fab3ecc0c8db10612`。
- 题目：https://github.com/openjung/core/blob/4d7917df1857e2dc04adc47fab3ecc0c8db10612/src/questions.ts
- 评分：https://github.com/openjung/core/blob/4d7917df1857e2dc04adc47fab3ecc0c8db10612/src/scoring.ts
- 译文来源记录：https://github.com/openjung/core/blob/4d7917df1857e2dc04adc47fab3ecc0c8db10612/TRANSLATIONS.md
- 数据许可：https://github.com/openjung/core/blob/4d7917df1857e2dc04adc47fab3ecc0c8db10612/LICENSE-DATA

题目、中文译文及其改编内容采用 **CC BY-NC-SA 4.0**：署名、非商业、相同方式共享，https://creativecommons.org/licenses/by-nc-sa/4.0/ 。仓库代码MIT不覆盖题目内容的许可。完整源码另有MIT许可，本项目未引入该npm依赖，也未直接执行下载的代码。

## 本项目保留和修改

`questions-openjung-zh.json` 保留上游32个题号、维度、英文/中文标题与两端描述。为适配当前页面，`scale-import.json` 和 `oejts-data.sql` 将标题合并到左右端显示；每题权重1，顺序按题号升序，右端为I/N/T/P。没有删除题目或偷偷修改方向。

这不是原PDF的逐字中文版。上游已做方向标准化、添加问题标题，且Q12为past/future（中文“过去/未来”），与原PDFpresent/future不同。本项目保留这项差异并记录，不声称完全还原原PDF。

采用独立评分版本 `OEJTS_OPENJUNG_32_V1`，复现此固定适配版的字母判定：每维8题，原始答案总和>24取右端，否则取左端E/S/F/J。系统按标准EI/SN/TF/JP顺序保存“第一字母端”的分数；因此某些维度的rawFirstScore与上游原始答案总和方向相反。展示百分比保留两位小数，上游采用整数显示。接近中点提示属于本系统的附加展示规则，不作为信度、效度或统计置信度。

`graph-example.json`、`graph-data.sql` 复用了题目文字与维度结构，这部分改编内容同样按CC BY-NC-SA4.0提供。图谱里的情境、工作方式、成长方向和行动关系是本项目另外编写的演示假设，明确标记PROJECT_DEMO；这些关联不是OEJTS/OpenJung作者研究结论，题库许可不能为其提供实证支持。包含二者的示例数据整体按CC BY-NC-SA4.0共享，不改变系统独立编写代码的许可状态。

上游提供中文文本不等于中文译本获得专家校核、跨文化等值或信效度验证。本项目没有完成这些验证，不用于正式心理诊断、招聘淘汰或能力评判。

如公开提交代码或答辩材料，请一并保留本目录、署名、许可链接、固定版本和以上改动说明。转为商业用途前需另行处理相关数据授权。
