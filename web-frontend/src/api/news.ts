import request from './request'

export const getNewsList = () => request.get('/news')
export const getNews = (newsId: number) => request.get(`/news/${newsId}`)
