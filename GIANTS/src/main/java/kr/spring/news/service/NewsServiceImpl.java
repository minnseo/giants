package kr.spring.news.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.spring.news.dao.NewsMapper;
import kr.spring.news.vo.NewsVO;

@Service
@Transactional
public class NewsServiceImpl implements NewsService{
	
	@Autowired
	NewsMapper newsMapper;
	
	@Override
	public List<NewsVO> selectNewsList(Map<String, Object> map) {
		return newsMapper.selectNewsList(map);
	}

	@Override
	public int selectNewsCount(Map<String, Object> map) {
		return newsMapper.selectNewsCount(map);
	}

	@Override
	public void insertNews(NewsVO news) {
		newsMapper.insertNews(news);
	}

	@Override
	public NewsVO selectNews(Integer news_num) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void updateHit(Integer news_num) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void udpateNews(NewsVO news) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deleteNews(Integer news_num) {
		// TODO Auto-generated method stub
		
	}

}
