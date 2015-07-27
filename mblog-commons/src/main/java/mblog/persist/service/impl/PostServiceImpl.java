/**
 * 
 */
package mblog.persist.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.SearchFactory;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import mblog.data.Attach;
import mblog.data.Post;
import mblog.data.Tag;
import mblog.lang.Consts;
import mblog.persist.dao.PostDao;
import mblog.persist.entity.PostPO;
import mblog.persist.entity.UserPO;
import mblog.persist.service.AttachService;
import mblog.persist.service.PostService;
import mblog.persist.service.TagService;
import mblog.utils.BeanMapUtils;
import mblog.utils.PreviewTextUtils;
import mtons.modules.lang.EntityStatus;
import mtons.modules.pojos.Paging;

/**
 * @author langhsu
 *
 */
public class PostServiceImpl implements PostService {
	@Autowired
	private PostDao postDao;
	@Autowired
	private AttachService attachService;
	@Autowired
	private TagService tagService;
	
	@Override
	@Transactional(readOnly = true)
	public void paging(Paging paging, int group, String ord, boolean loadImages) {
		List<PostPO> list = postDao.paging(paging, group, ord);
		
		List<Post> rets = new ArrayList<Post>();
		List<Long> ids = new ArrayList<Long>();

		list.forEach(po -> {
			ids.add(po.getId());
			rets.add(BeanMapUtils.copy(po, 0));
		});
		
		if (loadImages) {
			buildAttachs(rets, ids);
		}
		
		paging.setResults(rets);
	}
	
	@Override
	@Transactional(readOnly = true)
	public void pagingByUserId(Paging paging, long userId) {
		List<PostPO> list = postDao.pagingByUserId(paging, userId);
		
		List<Post> rets = new ArrayList<Post>();
		List<Long> ids = new ArrayList<Long>();

		list.forEach(po -> {
			ids.add(po.getId());
			rets.add(BeanMapUtils.copy(po ,0));
		});
		
		buildAttachs(rets, ids);
		
		paging.setResults(rets);
	}
	
	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public List<Post> search(Paging paging, String q) throws InterruptedException, IOException, InvalidTokenOffsetsException {
		FullTextSession fullTextSession = Search.getFullTextSession(postDao.getSession());
//	    fullTextSession.createIndexer().startAndWait();
	    SearchFactory sf = fullTextSession.getSearchFactory();
	    QueryBuilder qb = sf.buildQueryBuilder().forEntity(PostPO.class).get();

		org.apache.lucene.search.Query luceneQuery  = qb.keyword().onFields("title","summary","tags").matching(q).createQuery();

		FullTextQuery query = fullTextSession.createFullTextQuery(luceneQuery);
	    query.setFirstResult(paging.getFirstResult());
	    query.setMaxResults(paging.getMaxResults());

		//按Id排倒序
		Sort sort = new Sort(new SortField("id", SortField.Type.LONG, true));
		query.setSort(sort);

	    StandardAnalyzer standardAnalyzer = new StandardAnalyzer(); 
	    SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<span style='color:red;'>", "</span>");
        QueryScorer queryScorer = new QueryScorer(luceneQuery);
        Highlighter highlighter = new Highlighter(formatter, queryScorer);
        
	    List<PostPO> list = query.list();
	    int resultSize = query.getResultSize();
	    
	    List<Post> rets = new ArrayList<Post>();
	    List<Long> ids = new ArrayList<Long>();
	    
		for (PostPO po : list) {
			Post m = BeanMapUtils.copy(po ,0);

			String title = highlighter.getBestFragment(standardAnalyzer, "title", m.getTitle());
			String summary = highlighter.getBestFragment(standardAnalyzer, "summary", m.getSummary());
			String tags = highlighter.getBestFragment(standardAnalyzer, "tags", m.getTags());

			if (StringUtils.isNotEmpty(title)) {
				m.setTitle(title);
			}
			if (StringUtils.isNotEmpty(summary)) {
				m.setSummary(summary);
			}
			if (StringUtils.isNotEmpty(tags)) {
				m.setTags(tags);
			}
			rets.add(m);
			
			ids.add(po.getId());
		}
		
		buildAttachs(rets, ids);
		
		paging.setTotalCount(resultSize);
		paging.setResults(rets);
		return rets;
	}
	
	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public List<Post> searchByTag(Paging paigng, String tag) throws InterruptedException, IOException, InvalidTokenOffsetsException {
		FullTextSession fullTextSession = Search.getFullTextSession(postDao.getSession());
	    SearchFactory sf = fullTextSession.getSearchFactory();
	    QueryBuilder qb = sf.buildQueryBuilder().forEntity(PostPO.class).get();
	    org.apache.lucene.search.Query luceneQuery  = qb.keyword().onFields("tags").matching(tag).createQuery();

	    FullTextQuery query = fullTextSession.createFullTextQuery(luceneQuery);
	    query.setFirstResult(paigng.getFirstResult());
	    query.setMaxResults(paigng.getMaxResults());

		//按Id排倒序
		Sort sort = new Sort(new SortField("id", SortField.Type.LONG, true));
		query.setSort(sort);

		List<PostPO> list = query.list();
	    List<Long> ids = new ArrayList<Long>();
	    
	    int resultSize = query.getResultSize();
	    
	    List<Post> rets = new ArrayList<Post>();

		for (PostPO po : list) {
			Post m = BeanMapUtils.copy(po ,0);
			rets.add(m);
			
			ids.add(po.getId());
		}
		
		buildAttachs(rets, ids);
		
		paigng.setTotalCount(resultSize);
		paigng.setResults(rets);
		return rets;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<Post> findRecents(int maxResutls, long ignoreUserId) {
		List<PostPO> list = postDao.findRecents(maxResutls, ignoreUserId);
		List<Post> rets = new ArrayList<Post>();

		list.forEach(po -> rets.add(BeanMapUtils.copy(po, 0)));

		return rets;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<Post> findHots(int maxResutls, long ignoreUserId) {
		List<PostPO> list = postDao.findHots(maxResutls, ignoreUserId);
		List<Post> rets = new ArrayList<Post>();

		list.forEach(po -> rets.add(BeanMapUtils.copy(po, 0)));
		return rets;
	}
	
	@Override
	@Transactional(readOnly = true)
	public Map<Long, Post> findByIds(Set<Long> ids) {
		List<PostPO> list = postDao.findByIds(ids);
		Map<Long, Post> rets = new HashMap<Long, Post>();
		
		List<Long> imageIds = new ArrayList<Long>();

		list.forEach(po -> {
			rets.put(po.getId(), BeanMapUtils.copy(po, 0));

			if (po.getLastImageId() > 0) {
				imageIds.add(po.getLastImageId());
			}
		});
		
		Map<Long, Attach> ats = attachService.findByIds(imageIds);

		rets.forEach((k, v) -> {
			if (v.getLastImageId() > 0) {
				Attach a = ats.get(v.getLastImageId());
				v.setAlbum(a);
			}
		});
		return rets;
	}
	
	@Override
	@Transactional
	public void post(Post post) {
		PostPO po = new PostPO();

		BeanUtils.copyProperties(post, po, BeanMapUtils.POST_IGNORE);

		po.setAuthor(new UserPO(post.getAuthorId()));
		po.setCreated(new Date());
		po.setStatus(EntityStatus.ENABLED);

		// 处理摘要
		if (StringUtils.isBlank(post.getSummary())) {
			po.setSummary(trimSummary(post.getContent()));
		} else {
			po.setSummary(post.getSummary());
		}

		postDao.save(po);
		
		// attach handle
		if (post.getAlbums() != null) {
			long lastImageId = attachService.batchAdd(po.getId(), post.getAlbums());
			po.setLastImageId(lastImageId);
			po.setImages(post.getAlbums().size());
		}
		
		// tag handle
		if (StringUtils.isNotBlank(post.getTags())) {
			List<Tag> tags = BeanMapUtils.convertTags(po.getId(), post.getTags());
			tagService.batchPost(tags);
		}
	}
	
	@Override
	@Transactional
	public Post get(long id) {
		PostPO po = postDao.get(id);
		Post d = null;
		if (po != null) {
			d = BeanMapUtils.copy(po, 1);
			
			List<Attach> albs = attachService.findByTarget(d.getId());
			d.setAlbums(albs);
		}
		return d;
	}
	
	@Override
	@Transactional
	public void delete(long id) {
		PostPO po = postDao.get(id);
		if (po != null) {
			attachService.deleteByToId(id);
			postDao.delete(po);
		}
	}
	
	@Override
	@Transactional
	public void delete(long id, long authorId) {
		PostPO po = postDao.get(id);
		if (po != null) {
			Assert.isTrue(po.getAuthor().getId() == authorId, "认证失败");
			attachService.deleteByToId(id);
			postDao.delete(po);
		}
	}
	
	@Override
	@Transactional
	public void identityViews(long id) {
		PostPO po = postDao.get(id);
		if (po != null) {
			po.setViews(po.getViews() + Consts.IDENTITY_STEP);
		}
	}

	@Override
	@Transactional
	public void identityFavors(long id) {
		PostPO po = postDao.get(id);
		if (po != null) {
			po.setFavors(po.getFavors() + Consts.IDENTITY_STEP);
		}
	}
	
	@Override
	@Transactional
	public void identityComments(long id) {
		PostPO po = postDao.get(id);
		if (po != null) {
			po.setComments(po.getComments() + Consts.IDENTITY_STEP);
		}
	}
	
	/**
     * 截取文章内容
     * @param text
     * @return
     */
    private String trimSummary(String text){
        return PreviewTextUtils.truncateText(text, 126);
    }
    
    /**
	 * 更新文章方法
	 * @param p
	 */
    @Override
	@Transactional
	public void update(Post p){
    	PostPO po = postDao.get(p.getId());
    	
    	if (po != null) {
	    	po.setTitle(p.getTitle());//标题
	    	po.setSummary(trimSummary(p.getContent()));
	    	po.setContent(p.getContent());//内容
	    	po.setTags(p.getTags());//标签
    	}
	}

	@Override
	@Transactional
	public void updateImage(long id, long lastImageId) {
		postDao.updateImageId(id, lastImageId);
	}

	private void buildAttachs(List<Post> posts, List<Long> postIds) {
    	Map<Long, List<Attach>> attMap = attachService.findByTarget(postIds);

		posts.forEach(p -> p.setAlbums(attMap.get(p.getId())));
    }
}