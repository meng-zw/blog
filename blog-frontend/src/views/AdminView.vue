<template>
  <div class="admin-view">
    <div class="admin-container">
      <h1 class="page-title">后台管理</h1>

      <!-- 分类管理 -->
      <el-card class="admin-section">
        <template #header>
          <div class="section-header">
            <h2>文章分类管理</h2>
            <el-button type="primary" @click="showAddCategoryDialog">
              <Icon name="add" size="sm" />
              添加分类
            </el-button>
          </div>
        </template>

        <el-table :data="articleCategories" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="分类名称" />
          <el-table-column prop="created_at" label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.created_at) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="editCategory(row)">
                编辑
              </el-button>
              <el-button type="danger" text size="small" @click="deleteCategory(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 工具分类管理 -->
      <el-card class="admin-section">
        <template #header>
          <div class="section-header">
            <h2>工具分类管理</h2>
            <el-button type="primary" @click="showAddToolCategoryDialog">
              <Icon name="add" size="sm" />
              添加分类
            </el-button>
          </div>
        </template>

        <el-table :data="toolCategories" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="分类名称" />
          <el-table-column prop="created_at" label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.created_at) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="editToolCategory(row)">
                编辑
              </el-button>
              <el-button type="danger" text size="small" @click="deleteToolCategory(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 标签管理 -->
      <el-card class="admin-section">
        <template #header>
          <div class="section-header">
            <h2>标签管理</h2>
            <el-button type="primary" @click="showAddTagDialog">
              <Icon name="add" size="sm" />
              添加标签
            </el-button>
          </div>
        </template>

        <el-table :data="tags" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="标签名称" />
          <el-table-column prop="created_at" label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.created_at) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="editTag(row)">
                编辑
              </el-button>
              <el-button type="danger" text size="small" @click="deleteTag(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 分类编辑对话框 -->
    <el-dialog v-model="categoryDialogVisible" :title="editingCategory ? '编辑分类' : '添加分类'" width="400px">
      <el-form :model="categoryForm" label-width="80px">
        <el-form-item label="分类名称">
          <el-input v-model="categoryForm.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="分类类型">
          <el-select v-model="categoryForm.type" placeholder="请选择类型" :disabled="!!editingCategory">
            <el-option label="文章分类" value="article" />
            <el-option label="工具分类" value="tool" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 标签编辑对话框 -->
    <el-dialog v-model="tagDialogVisible" :title="editingTag ? '编辑标签' : '添加标签'" width="400px">
      <el-form :model="tagForm" label-width="80px">
        <el-form-item label="标签名称">
          <el-input v-model="tagForm.name" placeholder="请输入标签名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTag" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from '../utils/axios'
import Icon from '../components/Icon.vue'

const router = useRouter()

const articleCategories = ref<any[]>([])
const toolCategories = ref<any[]>([])
const tags = ref<any[]>([])
const categoryDialogVisible = ref(false)
const tagDialogVisible = ref(false)
const saving = ref(false)
const editingCategory = ref<any>(null)
const editingTag = ref<any>(null)

const categoryForm = ref({
  name: '',
  type: 'article'
})

const tagForm = ref({
  name: ''
})

const checkAdmin = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return false
  }
  // 检查用户是否是管理员
  const role = localStorage.getItem('role')
  if (role !== 'admin') {
    ElMessage.error('您没有权限访问此页面')
    router.push('/')
    return false
  }
  return true
}

const loadCategories = async () => {
  try {
    const [articleRes, toolRes] = await Promise.all([
      axios.get('/categories/article'),
      axios.get('/categories/tool')
    ])
    articleCategories.value = articleRes
    toolCategories.value = toolRes
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

const loadTags = async () => {
  try {
    const res = await axios.get('/tags')
    tags.value = res
  } catch (error) {
    console.error('加载标签失败:', error)
  }
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
}

const showAddCategoryDialog = () => {
  editingCategory.value = null
  categoryForm.value = { name: '', type: 'article' }
  categoryDialogVisible.value = true
}

const showAddToolCategoryDialog = () => {
  editingCategory.value = null
  categoryForm.value = { name: '', type: 'tool' }
  categoryDialogVisible.value = true
}

const editCategory = (row: any) => {
  editingCategory.value = row
  categoryForm.value = { name: row.name, type: row.type }
  categoryDialogVisible.value = true
}

const editToolCategory = (row: any) => {
  editingCategory.value = row
  categoryForm.value = { name: row.name, type: row.type }
  categoryDialogVisible.value = true
}

const saveCategory = async () => {
  if (!categoryForm.value.name.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  
  saving.value = true
  try {
    if (editingCategory.value) {
      await axios.put(`/categories/${editingCategory.value.id}`, categoryForm.value)
      ElMessage.success('分类更新成功')
    } else {
      await axios.post('/categories', categoryForm.value)
      ElMessage.success('分类添加成功')
    }
    categoryDialogVisible.value = false
    await loadCategories()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    saving.value = false
  }
}

const deleteCategory = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这个分类吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await axios.delete(`/categories/${id}`)
    ElMessage.success('分类删除成功')
    await loadCategories()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const deleteToolCategory = async (id: number) => {
  await deleteCategory(id)
}

const showAddTagDialog = () => {
  editingTag.value = null
  tagForm.value = { name: '' }
  tagDialogVisible.value = true
}

const editTag = (row: any) => {
  editingTag.value = row
  tagForm.value = { name: row.name }
  tagDialogVisible.value = true
}

const saveTag = async () => {
  if (!tagForm.value.name.trim()) {
    ElMessage.warning('请输入标签名称')
    return
  }
  
  saving.value = true
  try {
    if (editingTag.value) {
      await axios.put(`/tags/${editingTag.value.id}`, tagForm.value)
      ElMessage.success('标签更新成功')
    } else {
      await axios.post('/tags', tagForm.value)
      ElMessage.success('标签添加成功')
    }
    tagDialogVisible.value = false
    await loadTags()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    saving.value = false
  }
}

const deleteTag = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这个标签吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await axios.delete(`/tags/${id}`)
    ElMessage.success('标签删除成功')
    await loadTags()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

onMounted(() => {
  if (!checkAdmin()) return
  loadCategories()
  loadTags()
})
</script>

<style scoped>
.admin-view {
  max-width: 1000px;
  margin: 0 auto;
  padding: var(--space-6) var(--space-4);
}

.page-title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-6);
}

.admin-container {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.admin-section {
  border-radius: var(--border-radius-lg);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-header h2 {
  margin: 0;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

@media (max-width: 768px) {
  .admin-view {
    padding: var(--space-4) var(--space-3);
  }
}
</style>
