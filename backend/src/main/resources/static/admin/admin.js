'use strict';
// Chỉ giữ token trong bộ nhớ trang; tải lại trang sẽ cần đăng nhập lại.
let session = null,
    refreshPromise = null,
    editingId = null,
    page = 1,
    pages = 0,
    busy = false;
const el = (id) => document.getElementById(id);
const message = (text) => {
    el('message').textContent = text;
};
function renderSession() {
    el('loginPanel').hidden = !!session;
    el('workspace').hidden = !session;
    el('account').textContent = session ? `Quản trị: ${session.user.name}` : '';
}

async function http(path, method = 'GET', body = null, access = null) {
    const headers = { Accept: 'application/json' };
    if (body !== null) {
        headers['Content-Type'] = 'application/json';
    }
    if (access) {
        headers.Authorization = `Bearer ${access}`;
    }
    let response, result;
    try {
        response = await fetch('/api/v1/' + path, {
            method,
            headers,
            body: body === null ? null : JSON.stringify(body),
            cache: 'no-store',
            signal: AbortSignal.timeout(20000)
        });
        result = await response.json();
    } catch (_) {
        throw Object.assign(new Error('Không kết nối được server. Hãy thử lại.'), { status: 0 });
    }
    if (!response.ok) {
        throw Object.assign(
            new Error(
                response.status >= 500
                    ? 'Dịch vụ tạm thời chưa sẵn sàng.'
                    : result.message || 'Yêu cầu chưa thành công.'
            ),
            { status: response.status }
        );
    }
    return result.data;
}

async function request(path, method = 'GET', body = null) {
    if (!session) {
        throw new Error('Hãy đăng nhập quản trị.');
    }
    const original = session;
    try {
        return await http(path, method, body, original.access_token);
    } catch (error) {
        if (error.status !== 401) {
            throw error;
        }
    }
    if (!session) {
        throw new Error('Phiên đã kết thúc. Hãy đăng nhập lại.');
    }
    if (session === original) {
        // Các request cùng hết hạn chờ chung một lần đổi refresh token.
        if (!refreshPromise) {
            refreshPromise = http('auth/refresh', 'POST', { refreshToken: original.refresh_token })
                .then((fresh) => {
                    if (session !== original) {
                        throw new Error('Phiên đã thay đổi. Hãy đăng nhập lại.');
                    }
                    session = fresh;
                })
                .catch((error) => {
                    if (error.status === 401 || error.status === 403) {
                        session = null;
                        renderSession();
                    }
                    throw error;
                })
                .finally(() => {
                    refreshPromise = null;
                });
        }
        await refreshPromise;
    }
    if (!session) {
        throw new Error('Hãy đăng nhập lại.');
    }
    return http(path, method, body, session.access_token);
}

async function action(work) {
    // Khóa nút trong lúc xử lý để tránh gửi thao tác hai lần.
    if (busy) {
        return;
    }
    busy = true;
    document.querySelectorAll('button').forEach((b) => (b.disabled = true));
    try {
        await work();
    } catch (error) {
        message(error.message);
    } finally {
        busy = false;
        document.querySelectorAll('button').forEach((b) => (b.disabled = false));
        el('previous').disabled = page <= 1;
        el('next').disabled = page >= pages;
    }
}

function resetForm() {
    editingId = null;
    el('topicForm').reset();
    el('formTitle').textContent = 'Thêm chủ đề';
}

async function load() {
    const data = await request(`admin/topics?page=${page}&size=10`);
    pages = data.meta.pages;
    el('pageInfo').textContent = `Trang ${page} · ${data.meta.total} chủ đề`;
    el('topics').replaceChildren();
    for (const topic of data.result) {
        const article = document.createElement('article'),
            title = document.createElement('h3'),
            content = document.createElement('p');
        title.textContent = `${topic.name} · ${topic.level} · ${topic.active ? 'Đang bật' : 'Đang tắt'}`;
        content.textContent = `${topic.description || ''}\n\nVai AI: ${topic.scenario}`;
        const edit = document.createElement('button');
        edit.textContent = 'Sửa';
        edit.onclick = () => {
            editingId = topic.id;
            el('formTitle').textContent = 'Sửa chủ đề';
            for (const field of ['name', 'description', 'level', 'scenario']) {
                el(field).value = topic[field] || '';
            }
            el('topicForm').scrollIntoView({ behavior: 'smooth' });
        };
        const toggle = document.createElement('button');
        toggle.textContent = topic.active ? 'Tắt chủ đề' : 'Bật chủ đề';
        toggle.onclick = () =>
            action(async () => {
                // Đổi trạng thái chủ đề, giữ nguyên lịch sử các bài luyện đã tạo.
                await request(`admin/topics/${topic.id}/active`, 'PATCH', { active: !topic.active });
                message('Đã cập nhật trạng thái. Bài luyện cũ vẫn được giữ.');
                await load();
            });
        article.append(title, content, edit, toggle);
        el('topics').append(article);
    }
    if (!data.result.length) {
        el('topics').textContent = 'Chưa có chủ đề. Bạn có thể thêm ở biểu mẫu phía trên.';
    }
}

el('loginForm').onsubmit = (event) => {
    event.preventDefault();
    action(async () => {
        const result = await http('auth/login', 'POST', {
            email: el('email').value.trim(),
            password: el('password').value
        });
        el('password').value = '';
        if (result.user.role !== 'ADMIN') {
            throw new Error('Tài khoản này không có quyền quản trị. Hãy dùng tài khoản ADMIN riêng.');
        }
        session = result;
        page = 1;
        renderSession();
        message('Đã đăng nhập.');
        await load();
    });
};
el('topicForm').onsubmit = (event) => {
    event.preventDefault();
    action(async () => {
        const body = {
            name: el('name').value.trim(),
            description: el('description').value.trim() || null,
            level: el('level').value,
            scenario: el('scenario').value.trim()
        };
        if (!body.name || !body.scenario) {
            throw new Error('Tên và bối cảnh không được để trống.');
        }
        await request(editingId ? `admin/topics/${editingId}` : 'admin/topics', editingId ? 'PUT' : 'POST', body);
        resetForm();
        page = 1;
        message('Đã lưu chủ đề.');
        await load();
    });
};
el('logout').onclick = () =>
    action(async () => {
        await request('auth/logout', 'POST');
        session = null;
        el('topics').replaceChildren();
        resetForm();
        renderSession();
        message('Đã đăng xuất.');
    });
el('cancelEdit').onclick = resetForm;
el('reload').onclick = () =>
    action(async () => {
        page = 1;
        await load();
    });
el('previous').onclick = () =>
    action(async () => {
        page = Math.max(1, page - 1);
        await load();
    });
el('next').onclick = () =>
    action(async () => {
        page++;
        await load();
    });
renderSession();
