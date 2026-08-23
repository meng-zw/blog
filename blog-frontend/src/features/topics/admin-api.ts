import type { CategoryRequest, CategoryResponse, PageResponse, TagRequest, TagResponse, TopicResponse, TopicWriteRequest } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'
type W = Record<string, any>
export type AdminTopic = TopicResponse & { articleIds: number[]; coverMediaId: number | null }
const topic = (v: W): AdminTopic => ({ id:v.id,name:v.name,slug:v.slug,description:v.description,coverUrl:v.cover_url??v.coverUrl,coverMediaId:v.cover_media_id??v.coverMediaId??null,status:v.status,sortOrder:v.sort_order??v.sortOrder,articleIds:v.article_ids??v.articleIds??[] })
const cat = (v: W): CategoryResponse => ({ id:v.id,name:v.name,slug:v.slug,description:v.description,sortOrder:v.sort_order??v.sortOrder,scope:v.scope })
const topicBody=(v:TopicWriteRequest)=>({name:v.name,description:v.description,cover_media_id:v.coverMediaId,status:v.status,article_ids:v.articleIds,sort_order:v.sortOrder})
const page=<T>(v:W,map:(x:W)=>T):PageResponse<T>=>({items:(v.items??[]).map(map),page:v.page,size:v.size,total:v.total,totalPages:v.total_pages??v.totalPages})
export const listTopics=async(pageNumber=0,size=20,status?:string,keyword?:string)=>page(await http.get<W>('/admin/topics',{query:{page:pageNumber,size,status,keyword}}),topic)
export const loadTopic=async(id:number)=>topic(await http.get<W>(`/admin/topics/${id}`))
export const createTopic=async(v:TopicWriteRequest)=>topic(await http.post<W>('/admin/topics',topicBody(v)))
export const updateTopic=async(id:number,v:TopicWriteRequest)=>topic(await http.put<W>(`/admin/topics/${id}`,topicBody(v)))
export const removeTopic=(id:number)=>http.delete<void>(`/admin/topics/${id}`)
export const reorderTopicArticles=(id:number,ids:number[])=>http.put<void>(`/admin/topics/${id}/articles`,ids)
export const listCategories=async(pageNumber=0,size=50,scope?:string)=>page(await http.get<W>('/admin/taxonomy/categories',{query:{page:pageNumber,size,scope}}),cat)
const catBody=(v:CategoryRequest)=>({name:v.name,description:v.description,sort_order:v.sortOrder,scope:v.scope})
export const createCategory=async(v:CategoryRequest)=>cat(await http.post<W>('/admin/taxonomy/categories',catBody(v)))
export const updateCategory=async(id:number,v:CategoryRequest)=>cat(await http.put<W>(`/admin/taxonomy/categories/${id}`,catBody(v)))
export const removeCategory=(id:number)=>http.delete<void>(`/admin/taxonomy/categories/${id}`)
export const listTags=async(pageNumber=0,size=50,keyword?:string)=>page(await http.get<W>('/admin/taxonomy/tags',{query:{page:pageNumber,size,keyword}}),(v)=>v as TagResponse)
export const createTag=(v:TagRequest)=>http.post<TagResponse>('/admin/taxonomy/tags',v)
export const updateTag=(id:number,v:TagRequest)=>http.put<TagResponse>(`/admin/taxonomy/tags/${id}`,v)
export const removeTag=(id:number)=>http.delete<void>(`/admin/taxonomy/tags/${id}`)
