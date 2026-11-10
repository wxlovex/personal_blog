<script setup>
import BlogIcon from '@/components/BlogIcon.vue'
</script>
<template>
  <article class="about-page page-enter">
    <header class="page-heading"><p class="eyebrow">A LITTLE ABOUT THIS PLACE</p><h1>在文字之间，<br>留住一点自己的声音。</h1></header>
    <div class="about-content"><p>这里是行间，一本由你决定如何分享的日记。</p><p>日常的琐碎、突然冒出的念头、还没理清的心情，都可以放在这里。不必写得完整，不必考虑有多少人喜欢，也不需要向谁解释。</p><blockquote>写给自己，真实就很好。</blockquote><p>每个账号都有独立的记录空间。日记默认仅自己可见；只有你主动发布的文章，才会出现在广场。私人分类与补记始终保留在自己的空间里。</p><p>标题可以不填，正文第一行会成为这一页的名字。以后想到新的内容，可以修改原文，也可以在页尾留一段只有自己可见的补记。</p></div>
    <p class="feature-note">记录默认私密；你可以手动将某篇文章发布到广场，让其他人阅读。广场分为技术、日常、读书、娱乐，支持按标题搜索。公开后可随时撤回，私人分类和补记始终不会公开。</p>
    <p class="feature-note">日记按创建时间从新到旧排列，可以按标题搜索、按自己的分类整理。已有的 txt、md 文件也可以导入正文，填写标题后保存。</p>
    <router-link to="/" class="btn">回到我的日记<BlogIcon name="arrow-right" /></router-link>
  </article>
</template>
<style scoped>
.about-page { max-width: 680px; margin-inline: auto; padding-block: 15px 20px; }
.about-page h1 { line-height: 1.7; }
.about-content { font-size: .95rem; line-height: 2.1; }
.about-content p { margin-bottom: 24px; }
.feature-note { color: var(--text-muted); font-size: .9rem; line-height: 2; margin-bottom: 24px; }
blockquote { margin: 34px 0; padding: 16px 24px; border-left: 2px solid var(--accent); color: var(--accent); background: var(--accent-bg); font-family: var(--serif); font-size: 1.1rem; }
.about-page > .btn { margin-top: 18px; }
</style>
