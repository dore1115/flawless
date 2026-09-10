const csrfToken = document.querySelector('meta[name="_csrf"]').content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

async function postAttendance(url, button) {
    button.disabled = true;
    try {
        const headers = {};
        if (csrfHeader && csrfToken) {
            headers[csrfHeader] = csrfToken;
        }

        const res = await fetch(url, { method: 'POST', headers });
        if (!res.ok) {
            const msg = await res.text();
            alert(msg || '처리에 실패했습니다.');
            return;
        }
        location.reload();
    } catch (e) {
        console.error(e); // 콘솔에 실제 에러도 남기기
        alert('네트워크 오류가 발생했습니다.');
    } finally {
        button.disabled = false;
    }
}

document.getElementById('clock-in').addEventListener('click', e => postAttendance('/attendance/clockIn', e.currentTarget));
document.getElementById('clock-out').addEventListener('click', e => postAttendance('/attendance/clockOut', e.currentTarget));