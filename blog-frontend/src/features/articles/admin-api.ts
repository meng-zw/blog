import type { AdminArticleResponse, AdminArticleSummaryResponse, ArticleStatus, ArticleWriteRequest, CategoryResponse, PageResponse, TagResponse, TopicResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

type Wire = Record<string, any>
const category = (v: Wire | null): CategoryResponse | null => v && ({ id: v.id, name: v.name, slug: v.slug, description: v.description, sortOrder: v.sort_order ?? v.sortOrder, scope: v.scope })
const tag = (v: Wire): TagResponse => ({ id: v.id, name: v.name, slug: v.slug })
const topic = (v: Wire | null): TopicResponse | null => v && ({ id: v.id, name: v.name, slug: v.slug, description: v.description, coverUrl: v.cover_url ?? v.coverUrl, status: v.status, sortOrder: v.sort_order ?? v.sortOrder })
function summary(v: Wire): AdminArticleSummaryResponse { return { id:v.id, slug:v.slug, title:v.title, summary:v.summary, contentType:v.content_type ?? v.contentType, status:v.status, publishedAt:v.published_at ?? v.publishedAt, scheduledAt:v.scheduled_at ?? v.scheduledAt, coverUrl:v.cover_url ?? v.coverUrl, category:category(v.category), tags:(v.tags ?? []).map(tag) } }
const attachment = (v: Wire) => ({ mediaId:v.media_id ?? v.mediaId, displayName:v.display_name ?? v.displayName, contentType:v.content_type ?? v.contentType, byteSize:v.byte_size ?? v.byteSize, downloadUrl:v.download_url ?? v.downloadUrl })
function detail(v: Wire): AdminArticleResponse { return { ...summary(v), coverMediaId:v.cover_media_id ?? v.coverMediaId ?? null, markdownContent:v.markdown_content ?? v.markdownContent, renderedHtml:v.rendered_html ?? v.renderedHtml, topic:topic(v.topic), seoTitle:v.seo_title ?? v.seoTitle, seoDescription:v.seo_description ?? v.seoDescription, attachments:(v.attachments ?? []).map(attachment) } }
const write = (v: ArticleWriteRequest) => ({ title:v.title, slug:v.slug, summary:v.summary, markdown_content:v.markdownContent, content_type:v.contentType, cover_media_id:v.coverMediaId, category_id:v.categoryId, topic_id:v.topicId, tag_ids:v.tagIds, seo_title:v.seoTitle, seo_description:v.seoDescription, attachment_media_ids:v.attachmentMediaIds })
export async function listAdminArticles(params:{page:number,size:number,status?:ArticleStatus,contentType?:string,keyword?:string}):Promise<PageResponse<AdminArticleSummaryResponse>> { const w=await http.get<Wire>('/admin/articles',{query:params}); return {items:(w.items??[]).map(summary),page:w.page,size:w.size,total:w.total,totalPages:w.total_pages??w.totalPages} }
export async function lookupAdminArticles(ids:number[]):Promise<AdminArticleSummaryResponse[]> { return (await http.get<Wire[]>('/admin/articles/lookup',{query:{ids}})).map(summary) }
export async function loadAdminArticle(id:number){ return detail(await http.get<Wire>(`/admin/articles/${id}`)) }
export async function createArticle(v:ArticleWriteRequest){ return detail(await http.post<Wire>('/admin/articles',write(v))) }
export async function updateArticle(id:number,v:ArticleWriteRequest){ return detail(await http.put<Wire>(`/admin/articles/${id}`,write(v))) }
export async function publishArticle(id:number){ return detail(await http.post<Wire>(`/admin/articles/${id}/publish`)) }
export async function scheduleArticle(id:number,scheduledAt:string){ return detail(await http.post<Wire>(`/admin/articles/${id}/schedule`,{scheduled_at:scheduledAt})) }
export async function archiveArticle(id:number){ return detail(await http.post<Wire>(`/admin/articles/${id}/archive`)) }
export async function loadArticleOptions(){ const [categories,tags,topics]=await Promise.all([http.get<Wire>('/admin/taxonomy/categories',{query:{scope:'ARTICLE',page:0,size:50}}),http.get<Wire>('/admin/taxonomy/tags',{query:{page:0,size:50}}),http.get<Wire>('/admin/topics',{query:{page:0,size:50}})]); return {categories:(categories.items??[]).map((v:Wire)=>category(v)!),tags:(tags.items??[]).map(tag),topics:(topics.items??[]).map((v:Wire)=>topic(v)!)} }
