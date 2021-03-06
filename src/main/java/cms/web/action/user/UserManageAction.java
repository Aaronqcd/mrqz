package cms.web.action.user;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.DateTime;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;

import cms.bean.PageForm;
import cms.bean.PageView;
import cms.bean.QueryResult;
import cms.bean.topic.Comment;
import cms.bean.topic.Reply;
import cms.bean.topic.Tag;
import cms.bean.topic.Topic;
import cms.bean.topic.TopicIndex;
import cms.bean.user.User;
import cms.bean.user.UserCustom;
import cms.bean.user.UserGrade;
import cms.bean.user.UserInputValue;
import cms.service.setting.SettingService;
import cms.service.topic.CommentService;
import cms.service.topic.TagService;
import cms.service.topic.TopicIndexService;
import cms.service.topic.TopicService;
import cms.service.user.UserCustomService;
import cms.service.user.UserGradeService;
import cms.service.user.UserService;
import cms.utils.JsonUtils;
import cms.utils.PathUtil;
import cms.utils.RedirectPath;
import cms.utils.SHA;
import cms.utils.UUIDUtil;
import cms.utils.Verification;
import cms.web.action.FileManage;
import cms.web.action.SystemException;
import cms.web.action.TextFilterManage;
import cms.web.action.lucene.TopicLuceneManage;
import cms.web.action.thumbnail.ThumbnailManage;
import cms.web.action.topic.TopicManage;

/**
 * ????????????
 *
 */
@Controller
@RequestMapping("/control/user/manage") 
public class UserManageAction {
	
	@Resource(name="userServiceBean")
	private UserService userService;
	//????????????bean
	@Resource(name="userCustomServiceBean")
	private UserCustomService userCustomService;
	@Resource PointManage pointManage;
	@Resource SettingService settingService;
	
	@Resource(name = "userValidator") 
	private Validator validator; 
	@Resource UserManage userManage;
	
	@Resource UserGradeService userGradeService;
	
	@Resource TopicService topicService;
	@Resource CommentService commentService;
	@Resource TextFilterManage textFilterManage;
	@Resource TagService tagService;
	@Resource TopicManage topicManage;
	
	
	@Resource FileManage fileManage;
	@Resource ThumbnailManage thumbnailManage;
	
	@Resource TopicLuceneManage topicLuceneManage;
	@Resource TopicIndexService topicIndexService;
	
	/**
	 * ???????????? ??????
	 */
	@RequestMapping(params="method=show",method=RequestMethod.GET)
	public String show(ModelMap model,Long id,
			HttpServletRequest request, HttpServletResponse response) throws Exception {

		if(id == null){
			throw new SystemException("????????????");
		}
		
		User user = userService.findUserById(id);
		if(user == null){
			throw new SystemException("???????????????");
		}	
		user.setPassword(null);//???????????????
		user.setAnswer(null);//???????????????????????????
		user.setSalt(null);//???????????????
		
		List<UserGrade> userGradeList = userGradeService.findAllGrade();
		if(userGradeList != null && userGradeList.size() >0){
			for(UserGrade userGrade : userGradeList){//?????????????????? 
				if(user.getPoint() >= userGrade.getNeedPoint()){
					user.setGradeName(userGrade.getName());//?????????????????????????????????
					break;
				}
			} 
		}
		
		
		List<UserCustom> userCustomList = userCustomService.findAllUserCustom();
		if(userCustomList != null && userCustomList.size() >0){		
			Iterator <UserCustom> it = userCustomList.iterator();  
			while(it.hasNext()){  
				UserCustom userCustom = it.next();
				if(userCustom.isVisible() == false){//???????????????
					it.remove();  
					continue;
				}
				if(userCustom.getValue() != null && !"".equals(userCustom.getValue().trim())){
					LinkedHashMap<String,String> itemValue = JsonUtils.toGenericObject(userCustom.getValue(), new TypeReference<LinkedHashMap<String,String>>(){});
					userCustom.setItemValue(itemValue);
				}
				
			}
		}
		
		List<UserInputValue> userInputValueList= userCustomService.findUserInputValueByUserName(user.getId());
		if(userInputValueList != null && userInputValueList.size() >0){
			for(UserCustom userCustom : userCustomList){
				for(UserInputValue userInputValue : userInputValueList){
					if(userCustom.getId().equals(userInputValue.getUserCustomId())){
						userCustom.addUserInputValue(userInputValue);
					}
				}
			}
		}
		
		
		model.addAttribute("userCustomList", userCustomList);
		model.addAttribute("user",user);
		
		return "jsp/user/show_user";
	}
	
	
	
	/**
	 * ???????????? ??????????????????
	 */
	@RequestMapping(params="method=add",method=RequestMethod.GET)
	public String addUI(ModelMap model,User user,
			HttpServletRequest request, HttpServletResponse response) throws Exception {
		
		List<UserCustom> userCustomList = userCustomService.findAllUserCustom();
		if(userCustomList != null && userCustomList.size() >0){		
			Iterator <UserCustom> it = userCustomList.iterator();  
			while(it.hasNext()){  
				UserCustom userCustom = it.next();
				if(userCustom.isVisible() == false){//???????????????
					it.remove();  
					continue;
				}
				if(userCustom.getValue() != null && !"".equals(userCustom.getValue().trim())){
					LinkedHashMap<String,String> itemValue = JsonUtils.toGenericObject(userCustom.getValue(), new TypeReference<LinkedHashMap<String,String>>(){});
					userCustom.setItemValue(itemValue);
				}
				
			}
		}
		model.addAttribute("userCustomList", userCustomList);
		
		return "jsp/user/add_user";
	}
	
	
	/**
	 * ???????????? ????????????(?????????????????????)
	 */
	@RequestMapping(params="method=add",method=RequestMethod.POST)
	public String add(User formbean,BindingResult result,ModelMap model,
			HttpServletRequest request, HttpServletResponse response) throws Exception {
		
		//??????
		Map<String,String> error = new HashMap<String,String>();
		//????????????????????????????????????
		List<UserCustom> userCustomList = userCustomService.findAllUserCustom();
		if(userCustomList != null && userCustomList.size() >0){	
			for(UserCustom userCustom : userCustomList){
				//???????????????????????????????????????????????????
				List<UserInputValue> userInputValueList = new ArrayList<UserInputValue>();
				
				if(userCustom.isVisible() == true){//??????
					if(userCustom.getValue() != null && !"".equals(userCustom.getValue().trim())){
						LinkedHashMap<String,String> itemValue = JsonUtils.toGenericObject(userCustom.getValue(), new TypeReference<LinkedHashMap<String,String>>(){});
						userCustom.setItemValue(itemValue);
					}
					if(userCustom.getChooseType().equals(1)){//1.?????????
						String userCustom_value = request.getParameter("userCustom_"+userCustom.getId());
						
						if(userCustom_value != null && !"".equals(userCustom_value.trim())){
							UserInputValue userInputValue = new UserInputValue();
							userInputValue.setUserCustomId(userCustom.getId());
							userInputValue.setContent(userCustom_value.trim());
							userInputValueList.add(userInputValue);
							
							if(userCustom.getMaxlength() != null && userCustom_value.length() > userCustom.getMaxlength()){
								error.put("userCustom_"+userCustom.getId(), "????????????"+userCustom_value.length()+"?????????");
							}
							
							int fieldFilter = userCustom.getFieldFilter();//??????????????????    0.???  1.?????????????????????  2.?????????????????????  3.??????????????????????????????  4.?????????????????????  5.?????????????????????
							switch(fieldFilter){
								case 1 : //????????????
									if(Verification.isPositiveIntegerZero(userCustom_value.trim()) == false){
										error.put("userCustom_"+userCustom.getId(), "?????????????????????");
									}
								  break; 
								case 2 : //????????????
									if(Verification.isLetter(userCustom_value.trim()) == false){
										error.put("userCustom_"+userCustom.getId(), "?????????????????????");
									}
								  break;
								case 3 : //???????????????????????????
									if(Verification.isNumericLetters(userCustom_value.trim()) == false){
										error.put("userCustom_"+userCustom.getId(), "??????????????????????????????");
									}
								  break;
								case 4 : //??????????????????
									if(Verification.isChineseCharacter(userCustom_value.trim()) == false){
										error.put("userCustom_"+userCustom.getId(), "?????????????????????");
									}
								  break;
								case 5 : //?????????????????????
									if(userCustom_value.trim().matches(userCustom.getRegular())== false){
										error.put("userCustom_"+userCustom.getId(), "????????????");
									}
								  break;
							//	default:
							}
						}else{
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
							
						}	
						userCustom.setUserInputValueList(userInputValueList);
					}else if(userCustom.getChooseType().equals(2)){//2.????????????
						String userCustom_value = request.getParameter("userCustom_"+userCustom.getId());
						
						if(userCustom_value != null && !"".equals(userCustom_value.trim())){
							
							String itemValue = userCustom.getItemValue().get(userCustom_value.trim());
							if(itemValue != null ){
								UserInputValue userInputValue = new UserInputValue();
								userInputValue.setUserCustomId(userCustom.getId());
								userInputValue.setOptions(userCustom_value.trim());
								userInputValueList.add(userInputValue);
								
							}else{
								if(userCustom.isRequired() == true){//????????????	
									error.put("userCustom_"+userCustom.getId(), "?????????");
								}
							}
							
						}else{
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						userCustom.setUserInputValueList(userInputValueList);	
						
					}else if(userCustom.getChooseType().equals(3)){//3.????????????
						String[] userCustom_value_arr = request.getParameterValues("userCustom_"+userCustom.getId());
						
						if(userCustom_value_arr != null && userCustom_value_arr.length >0){
							for(String userCustom_value : userCustom_value_arr){
								
								if(userCustom_value != null && !"".equals(userCustom_value.trim())){
									
									String itemValue = userCustom.getItemValue().get(userCustom_value.trim());
									if(itemValue != null ){
										UserInputValue userInputValue = new UserInputValue();
										userInputValue.setUserCustomId(userCustom.getId());
										userInputValue.setOptions(userCustom_value.trim());
										userInputValueList.add(userInputValue);
									}
									
									
								}
							}
						}else{
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						if(userInputValueList.size() == 0){
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						userCustom.setUserInputValueList(userInputValueList);	
						
					}else if(userCustom.getChooseType().equals(4)){//4.????????????
						String[] userCustom_value_arr = request.getParameterValues("userCustom_"+userCustom.getId());
						
						if(userCustom_value_arr != null && userCustom_value_arr.length >0){
							for(String userCustom_value : userCustom_value_arr){
								
								if(userCustom_value != null && !"".equals(userCustom_value.trim())){
									
									String itemValue = userCustom.getItemValue().get(userCustom_value.trim());
									if(itemValue != null ){
										UserInputValue userInputValue = new UserInputValue();
										userInputValue.setUserCustomId(userCustom.getId());
										userInputValue.setOptions(userCustom_value.trim());
										userInputValueList.add(userInputValue);
									}
									
									
								}
							}
						}else{
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						if(userInputValueList.size() == 0){
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						userCustom.setUserInputValueList(userInputValueList);	
					}else if(userCustom.getChooseType().equals(5)){// 5.?????????
						String userCustom_value = request.getParameter("userCustom_"+userCustom.getId());
						
						if(userCustom_value != null && !"".equals(userCustom_value.trim())){
							UserInputValue userInputValue = new UserInputValue();
							userInputValue.setUserCustomId(userCustom.getId());
							userInputValue.setContent(userCustom_value);
							userInputValueList.add(userInputValue);
							
						}else{
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						userCustom.setUserInputValueList(userInputValueList);
					}
				}
			}
		}

		
		//????????????
		this.validator.validate(formbean, result); 
		if (result.hasErrors() || error.size() >0) { 
			model.addAttribute("user",formbean);
			if(userCustomList != null && userCustomList.size() >0){		
				Iterator <UserCustom> it = userCustomList.iterator();  
				while(it.hasNext()){  
					UserCustom userCustom = it.next();
					if(userCustom.isVisible() == false){//???????????????
						it.remove();  
						continue;
					}
					if(userCustom.getValue() != null && !"".equals(userCustom.getValue().trim())){
						LinkedHashMap<String,String> itemValue = JsonUtils.toGenericObject(userCustom.getValue(), new TypeReference<LinkedHashMap<String,String>>(){});
						userCustom.setItemValue(itemValue);
					}
					
				}
			}
		
			model.addAttribute("userCustomList", userCustomList);
			model.addAttribute("error", error);
			return "jsp/user/add_user";
		} 
		
		
		User user = new User();
		user.setSalt(UUIDUtil.getUUID32());
		user.setUserName(formbean.getUserName().trim());
		if(formbean.getNickname() != null && !"".equals(formbean.getNickname().trim())){
			user.setNickname(formbean.getNickname().trim());
		}
		
		//??????
		user.setPassword(SHA.sha256Hex(SHA.sha256Hex(formbean.getPassword().trim())+"["+user.getSalt()+"]"));
		user.setEmail(formbean.getEmail().trim());
		user.setIssue(formbean.getIssue().trim());
		//?????????????????????  ????????????????????????sha256  ??????sha256??????
		user.setAnswer(SHA.sha256Hex(SHA.sha256Hex(formbean.getAnswer().trim())));

		user.setRegistrationDate(new Date());
		user.setRemarks(formbean.getRemarks());
		user.setState(formbean.getState());
		
		user.setMobile(formbean.getMobile().trim());
		user.setRealNameAuthentication(formbean.isRealNameAuthentication());
		//????????????????????????
		user.setAllowUserDynamic(formbean.getAllowUserDynamic());
		user.setSecurityDigest(new Date().getTime());
		
		//???????????????????????????????????????????????????
		List<UserInputValue> all_userInputValueList = new ArrayList<UserInputValue>();
	
		if(userCustomList != null && userCustomList.size() >0){	
			for(UserCustom userCustom : userCustomList){
				all_userInputValueList.addAll(userCustom.getUserInputValueList());
			}
		}

		try {
			userService.saveUser(user,all_userInputValueList);
		} catch (Exception e) {
			throw new SystemException("??????????????????");
		//	e.printStackTrace();
		}
		//????????????
		userManage.delete_cache_findUserById(user.getId());
		userManage.delete_cache_findUserByUserName(user.getUserName());
		
		
		request.setAttribute("message", "??????????????????");
		request.setAttribute("urladdress", RedirectPath.readUrl("control.user.list"));
		return "jsp/common/message";
	}
	
	/**
	 * ???????????? ??????????????????
	 */
	@RequestMapping(params="method=edit",method=RequestMethod.GET)
	public String editUI(User formbean,ModelMap model,
			HttpServletRequest request, HttpServletResponse response) throws Exception {
		if(formbean.getId() == null){
			throw new SystemException("????????????");
		}
		
		User user = userService.findUserById(formbean.getId());
		user.setPassword(null);//???????????????
		user.setAnswer(null);//???????????????????????????
		user.setSalt(null);//???????????????
		
		List<UserCustom> userCustomList = userCustomService.findAllUserCustom();
		if(userCustomList != null && userCustomList.size() >0){		
			Iterator <UserCustom> it = userCustomList.iterator();  
			while(it.hasNext()){  
				UserCustom userCustom = it.next();
				if(userCustom.isVisible() == false){//???????????????
					it.remove();  
					continue;
				}
				if(userCustom.getValue() != null && !"".equals(userCustom.getValue().trim())){
					LinkedHashMap<String,String> itemValue = JsonUtils.toGenericObject(userCustom.getValue(), new TypeReference<LinkedHashMap<String,String>>(){});
					userCustom.setItemValue(itemValue);
				}
				
			}
		}
		
		List<UserInputValue> userInputValueList= userCustomService.findUserInputValueByUserName(user.getId());
		if(userInputValueList != null && userInputValueList.size() >0){
			for(UserCustom userCustom : userCustomList){
				for(UserInputValue userInputValue : userInputValueList){
					if(userCustom.getId().equals(userInputValue.getUserCustomId())){
						userCustom.addUserInputValue(userInputValue);
					}
				}
			}
		}
		
		
		model.addAttribute("userCustomList", userCustomList);
	
		
		
		model.addAttribute("user",user);
		
		return "jsp/user/edit_user";	
	}
	/**
	 * ???????????? ??????
	 * @param formbean
	 * @param model
	 * @param pageForm
	 * @param jumpStatus ????????????  ????????????????????????-10??????????????????(???????????????[-10????????????  -12:???????????????]) -1???????????????????????????
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(params="method=edit",method=RequestMethod.POST)
	public String edit(User formbean,ModelMap model,PageForm pageForm,Integer jumpStatus,
			HttpServletRequest request, HttpServletResponse response) throws Exception {
		if(formbean.getId() == null){
			throw new SystemException("????????????");
		}
		User user = userService.findUserById(formbean.getId());
		
		if(!user.getUserVersion().equals(formbean.getUserVersion())){
			throw new SystemException("????????????????????????");
		}
		User new_user = new User();
		
		//??????
		Map<String,String> error = new HashMap<String,String>();
		
		
		List<UserCustom> userCustomList = userCustomService.findAllUserCustom();
		//????????????????????????????????????
		if(userCustomList != null && userCustomList.size() >0){	
			for(UserCustom userCustom : userCustomList){
				//???????????????????????????????????????????????????
				List<UserInputValue> userInputValueList = new ArrayList<UserInputValue>();
				
				if(userCustom.isVisible() == true){//??????
					if(userCustom.getValue() != null && !"".equals(userCustom.getValue().trim())){
						LinkedHashMap<String,String> itemValue = JsonUtils.toGenericObject(userCustom.getValue(), new TypeReference<LinkedHashMap<String,String>>(){});
						userCustom.setItemValue(itemValue);
					}
					if(userCustom.getChooseType().equals(1)){//1.?????????
						String userCustom_value = request.getParameter("userCustom_"+userCustom.getId());
						
						if(userCustom_value != null && !"".equals(userCustom_value.trim())){
							UserInputValue userInputValue = new UserInputValue();
							userInputValue.setUserCustomId(userCustom.getId());
							userInputValue.setContent(userCustom_value.trim());
							userInputValueList.add(userInputValue);

							if(userCustom.getMaxlength() != null && userCustom_value.length() > userCustom.getMaxlength()){
								error.put("userCustom_"+userCustom.getId(), "????????????"+userCustom_value.length()+"?????????");
							}
							

							int fieldFilter = userCustom.getFieldFilter();//??????????????????    0.???  1.?????????????????????  2.?????????????????????  3.??????????????????????????????  4.?????????????????????  5.?????????????????????
							switch(fieldFilter){
								case 1 : //????????????
									if(Verification.isPositiveIntegerZero(userCustom_value.trim()) == false){
										error.put("userCustom_"+userCustom.getId(), "?????????????????????");
									}
								  break; 
								case 2 : //????????????
									if(Verification.isLetter(userCustom_value.trim()) == false){
										error.put("userCustom_"+userCustom.getId(), "?????????????????????");
									}
								  break;
								case 3 : //???????????????????????????
									if(Verification.isNumericLetters(userCustom_value.trim()) == false){
										error.put("userCustom_"+userCustom.getId(), "??????????????????????????????");
									}
								  break;
								case 4 : //??????????????????
									if(Verification.isChineseCharacter(userCustom_value.trim()) == false){
										error.put("userCustom_"+userCustom.getId(), "?????????????????????");
									}
								  break;
								case 5 : //?????????????????????
									if(userCustom_value.trim().matches(userCustom.getRegular())== false){
										error.put("userCustom_"+userCustom.getId(), "????????????");
									}
								  break;
							//	default:
							}
						}else{
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
							
						}	
						userCustom.setUserInputValueList(userInputValueList);
					}else if(userCustom.getChooseType().equals(2)){//2.????????????
						String userCustom_value = request.getParameter("userCustom_"+userCustom.getId());
						
						if(userCustom_value != null && !"".equals(userCustom_value.trim())){
							
							String itemValue = userCustom.getItemValue().get(userCustom_value.trim());
							if(itemValue != null ){
								UserInputValue userInputValue = new UserInputValue();
								userInputValue.setUserCustomId(userCustom.getId());
								userInputValue.setOptions(userCustom_value.trim());
								userInputValueList.add(userInputValue);
								
							}else{
								if(userCustom.isRequired() == true){//????????????	
									error.put("userCustom_"+userCustom.getId(), "?????????");
								}
							}
							
						}else{
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						userCustom.setUserInputValueList(userInputValueList);	
						
					}else if(userCustom.getChooseType().equals(3)){//3.????????????
						String[] userCustom_value_arr = request.getParameterValues("userCustom_"+userCustom.getId());
						
						if(userCustom_value_arr != null && userCustom_value_arr.length >0){
							for(String userCustom_value : userCustom_value_arr){
								
								if(userCustom_value != null && !"".equals(userCustom_value.trim())){
									
									String itemValue = userCustom.getItemValue().get(userCustom_value.trim());
									if(itemValue != null ){
										UserInputValue userInputValue = new UserInputValue();
										userInputValue.setUserCustomId(userCustom.getId());
										userInputValue.setOptions(userCustom_value.trim());
										userInputValueList.add(userInputValue);
									}	
								}
							}
						}else{
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						if(userInputValueList.size() == 0){
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						userCustom.setUserInputValueList(userInputValueList);	
						
					}else if(userCustom.getChooseType().equals(4)){//4.????????????
						String[] userCustom_value_arr = request.getParameterValues("userCustom_"+userCustom.getId());
						
						if(userCustom_value_arr != null && userCustom_value_arr.length >0){
							for(String userCustom_value : userCustom_value_arr){
								
								if(userCustom_value != null && !"".equals(userCustom_value.trim())){
									
									String itemValue = userCustom.getItemValue().get(userCustom_value.trim());
									if(itemValue != null ){
										UserInputValue userInputValue = new UserInputValue();
										userInputValue.setUserCustomId(userCustom.getId());
										userInputValue.setOptions(userCustom_value.trim());
										userInputValueList.add(userInputValue);
									}
								}
							}
						}else{
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						if(userInputValueList.size() == 0){
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						userCustom.setUserInputValueList(userInputValueList);	
					}else if(userCustom.getChooseType().equals(5)){// 5.?????????
						String userCustom_value = request.getParameter("userCustom_"+userCustom.getId());
						
						if(userCustom_value != null && !"".equals(userCustom_value.trim())){
							UserInputValue userInputValue = new UserInputValue();
							userInputValue.setUserCustomId(userCustom.getId());
							userInputValue.setContent(userCustom_value);
							userInputValueList.add(userInputValue);
							
						}else{
							if(userCustom.isRequired() == true){//????????????	
								error.put("userCustom_"+userCustom.getId(), "?????????");
							}
						}
						userCustom.setUserInputValueList(userInputValueList);
					}
				}
			}
		}
		
		
		//????????????
		if(formbean.getNickname() != null && !"".equals(formbean.getNickname().trim())){
			if(formbean.getNickname().length()>15){
				error.put("nickname", "??????????????????15?????????");
			}
			
			
			User u = userService.findUserByNickname(formbean.getNickname().trim());
			if(u != null){
				if(user.getNickname() == null || "".equals(user.getNickname()) || !formbean.getNickname().trim().equals(user.getNickname())){
					error.put("nickname", "??????????????????");
				}
				
			}
			new_user.setNickname(formbean.getNickname().trim());
		}else{
			new_user.setNickname(null);
		}
		if(formbean.getPassword() != null && !"".equals(formbean.getPassword().trim())){//??????
			if(formbean.getPassword().length()>30){
				error.put("password", "??????????????????30?????????");
			}
			//??????
			new_user.setPassword(SHA.sha256Hex(SHA.sha256Hex(formbean.getPassword().trim())+"["+user.getSalt()+"]"));
			new_user.setSecurityDigest(new Date().getTime());
		}else{
			new_user.setPassword(user.getPassword());
			new_user.setSecurityDigest(user.getSecurityDigest());
		}
		if(formbean.getIssue() != null && !"".equals(formbean.getIssue().trim())){//??????????????????
			if(formbean.getIssue().length()>50){
				error.put("issue", "??????????????????????????????50?????????");
			}
			new_user.setIssue(formbean.getIssue().trim());
		}else{
			error.put("issue", "??????????????????????????????");
		}
		if(formbean.getAnswer() != null && !"".equals(formbean.getAnswer().trim())){//??????????????????
			if(formbean.getAnswer().length()>50){
				error.put("answer", "??????????????????????????????50?????????");
			}
			//?????????????????????  ????????????????????????sha256  ??????sha256??????
			new_user.setAnswer(SHA.sha256Hex(SHA.sha256Hex(formbean.getAnswer().trim())));
		}else{
			new_user.setAnswer(user.getAnswer());
		}
		if(formbean.getEmail() != null && !"".equals(formbean.getEmail().trim())){//??????
			if(Verification.isEmail(formbean.getEmail().trim()) == false){
				error.put("email", "Email???????????????");
			}
			if(formbean.getEmail().trim().length()>60){
				error.put("email", "Email??????????????????60?????????");
			}
			new_user.setEmail(formbean.getEmail().trim());
		}
		//??????
		if(formbean.getMobile() != null && !"".equals(formbean.getMobile().trim())){
	    	if(formbean.getMobile().trim().length() >18){
				error.put("mobile", "??????????????????");
			}else{
				boolean mobile_verification = Verification.isPositiveInteger(formbean.getMobile().trim());//?????????
				if(!mobile_verification){
					error.put("mobile", "?????????????????????");
				}else{
					new_user.setMobile(formbean.getMobile().trim());
				}
			}
	    }
		//????????????
		new_user.setRealNameAuthentication(formbean.isRealNameAuthentication());
		//????????????????????????
		new_user.setAllowUserDynamic(formbean.getAllowUserDynamic());

		//????????????
		if(formbean.getState() == null){
			error.put("state", "????????????????????????");
		}else{
			if(formbean.getState() >2 || formbean.getState() <1){
				error.put("state", "??????????????????");
			}
			new_user.setState(formbean.getState());
		}
		new_user.setId(user.getId());
		new_user.setUserName(user.getUserName());
		//??????
		new_user.setRemarks(formbean.getRemarks());
		new_user.setUserVersion(formbean.getUserVersion());
		if(error.size() >0){
			model.addAttribute("error",error);
			formbean.setUserName(user.getUserName());
			formbean.setUserVersion(user.getUserVersion());
			model.addAttribute("user",formbean);
			
			
			if(userCustomList != null && userCustomList.size() >0){		
				Iterator <UserCustom> it = userCustomList.iterator();  
				while(it.hasNext()){  
					UserCustom userCustom = it.next();
					if(userCustom.isVisible() == false){//???????????????
						it.remove();  
						continue;
					}
					if(userCustom.getValue() != null && !"".equals(userCustom.getValue().trim())){
						LinkedHashMap<String,String> itemValue = JsonUtils.toGenericObject(userCustom.getValue(), new TypeReference<LinkedHashMap<String,String>>(){});
						userCustom.setItemValue(itemValue);
					}
					
				}
			}
			model.addAttribute("userCustomList", userCustomList);
			return "jsp/user/edit_user";	
		}
		
		List<UserInputValue> userInputValueList= userCustomService.findUserInputValueByUserName(user.getId());
		
		//??????????????????????????????????????????
		List<UserInputValue> add_userInputValue = new ArrayList<UserInputValue>();
		//????????????????????????????????????Id??????
		List<Long> delete_userInputValueIdList = new ArrayList<Long>();
		if(userCustomList != null && userCustomList.size() >0){	
			for(UserCustom userCustom : userCustomList){
				List<UserInputValue> new_userInputValueList = userCustom.getUserInputValueList();
				if(new_userInputValueList != null && new_userInputValueList.size() >0){
					A:for(UserInputValue new_userInputValue : new_userInputValueList){
						if(userInputValueList != null && userInputValueList.size() >0){
							for(UserInputValue old_userInputValue : userInputValueList){
								if(new_userInputValue.getUserCustomId().equals(old_userInputValue.getUserCustomId())){
									if(new_userInputValue.getOptions().equals("-1")){
										
										if(new_userInputValue.getContent() == null){
											if(old_userInputValue.getContent() == null){
												userInputValueList.remove(old_userInputValue);
												continue A;
											}
										}else{
											if(new_userInputValue.getContent().equals(old_userInputValue.getContent())){
												userInputValueList.remove(old_userInputValue);
												continue A;
											}
										}
										
									}else{
										if(new_userInputValue.getOptions().equals(old_userInputValue.getOptions())){
											userInputValueList.remove(old_userInputValue);
											continue A;
										}
									}
								}	
							}
						}
						add_userInputValue.add(new_userInputValue);
					}
				}
			}
		}
		if(userInputValueList != null && userInputValueList.size() >0){
			for(UserInputValue old_userInputValue : userInputValueList){
				delete_userInputValueIdList.add(old_userInputValue.getId());
			}
		}
		
		userService.updateUser(new_user,add_userInputValue,delete_userInputValueIdList);

		userManage.delete_userState(new_user.getUserName());
		
		//????????????
		userManage.delete_cache_findUserById(user.getId());
		userManage.delete_cache_findUserByUserName(user.getUserName());

		
		if(jumpStatus != null && jumpStatus<= -10){
			model.addAttribute("jumpStatus",jumpStatus);//????????????
			//???????????????????????????
			return "jsp/admin/frameCallback";
		}
		
		
		request.setAttribute("message", "??????????????????");
		request.setAttribute("urladdress", RedirectPath.readUrl("control.user.list")+"?page="+pageForm.getPage());
		return "jsp/common/message";
	}
	
	/**
	 * ??????  ??????
	 * @param userId ??????Id??????
	 * @param queryState ????????????  null???true:????????????  false:?????????
	 */
	@RequestMapping(params="method=delete",method=RequestMethod.POST)
	@ResponseBody//????????????ajax,?????????????????????
	public String delete(ModelMap model,Long[] userId,Boolean queryState,
			HttpServletRequest request, HttpServletResponse response) throws Exception {
		if(userId != null && userId.length >0){
			List<Long> idList = new ArrayList<Long>();
			List<String> userNameList = new ArrayList<String>();//??????????????????
			for(Long l :userId){
				if(l != null){
					idList.add(l);
				}
			}
			if(idList != null && idList.size() >0){
				List<User> userList = userService.findUserByUserIdList(idList);
				if(userList != null && userList.size() >0){
					for(User user : userList){
						userNameList.add(user.getUserName());
						
					}
					if(queryState != null && queryState != true){//????????????
						for(User user : userList){
							//????????????????????????
							topicManage.deleteTopicFile(user.getUserName(), false);
							
							//??????????????????
							topicManage.deleteCommentFile(user.getUserName(), false);

							DateTime dateTime = new DateTime(user.getRegistrationDate());     
							String date = dateTime.toString("yyyy-MM-dd");
							
							String pathFile = "file"+File.separator+"avatar"+File.separator + date +File.separator  +user.getAvatarName();
							//????????????
							fileManage.deleteFile(pathFile);
							
							String pathFile_100 = "file"+File.separator+"avatar"+File.separator + date +File.separator +"100x100" +File.separator+user.getAvatarName();
							//????????????100*100
							fileManage.deleteFile(pathFile_100);
							
						}
						
						
						
						int i = userService.delete(idList,userNameList);
						
						for(User user : userList){
							//????????????????????????
							topicIndexService.addTopicIndex(new TopicIndex(user.getUserName(),4));
							
							
							//????????????????????????
							userManage.delete_userState(user.getUserName());
							//????????????
							userManage.delete_cache_findUserById(user.getId());
							userManage.delete_cache_findUserByUserName(user.getUserName());
							
							
						}

						
						if(i >0){
							return "1";
						}
					}else{//????????????
						int i = userService.markDelete(idList);
						//????????????????????????
						for(User user : userList){
							userManage.delete_userState(user.getUserName());
							//????????????
							userManage.delete_cache_findUserById(user.getId());
							userManage.delete_cache_findUserByUserName(user.getUserName());
						}
						if(i >0){
							return "1";
						}
					}
				}
				
	
			}
		}
		return "0";
	}
	/**
	 * ??????
	 * @param model
	 * @param userId ??????Id??????
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(params="method=reduction",method=RequestMethod.POST)
	@ResponseBody//????????????ajax,?????????????????????
	public String reduction(ModelMap model,Long[] userId,
			HttpServletResponse response) throws Exception {
		if(userId != null && userId.length>0){
			
			List<User> userList = userService.findUserByUserIdList(Arrays.asList(userId));
			if(userList != null && userList.size() >0){
				
				for(User user :userList){
					if(user.getState().equals(11)){ //1:????????????   2:????????????   11: ??????????????????   12: ??????????????????
						user.setState(1);
					}else if(user.getState().equals(12)){
						user.setState(2);
					}
					
				}
				userService.reductionUser(userList);
				
				//????????????????????????
				for(User user :userList){
					userManage.delete_userState(user.getUserName());
					//????????????
					userManage.delete_cache_findUserById(user.getId());
					userManage.delete_cache_findUserByUserName(user.getUserName());
				}
				
				
				return "1";
			}	
		}
		return "0";
	}
	

	
	/**
	 * ???????????????
	 * 
	 * @param pageForm
	 * @param model
	 * @param userName ????????????
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(params="method=allTopic",method=RequestMethod.GET)
	public String allTopic(PageForm pageForm,ModelMap model,String userName,
			HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		if(userName != null && !"".equals(userName.trim())){
			StringBuffer jpql = new StringBuffer("");
			//???????????????
			List<Object> params = new ArrayList<Object>();

			jpql.append(" and o.userName=?"+ (params.size()+1));
			params.add(userName.trim());
			
			//???????????????and
			String _jpql = org.apache.commons.lang3.StringUtils.difference(" and", jpql.toString());
			
			PageView<Topic> pageView = new PageView<Topic>(settingService.findSystemSetting_cache().getBackstagePageNumber(),pageForm.getPage(),10);
			//?????????
			int firstindex = (pageForm.getPage()-1)*pageView.getMaxresult();;	
			//??????
			LinkedHashMap<String,String> orderby = new LinkedHashMap<String,String>();
			
			orderby.put("id", "desc");//??????id??????????????????
			
			
			//?????????????????????
			QueryResult<Topic> qr = topicService.getScrollData(Topic.class, firstindex, pageView.getMaxresult(), _jpql, params.toArray(),orderby);
			if(qr != null && qr.getResultlist() != null && qr.getResultlist().size() >0){
				List<Tag> tagList = tagService.findAllTag();
				if(tagList != null && tagList.size() >0){
					for(Topic topic : qr.getResultlist()){
						for(Tag tag : tagList){
							if(topic.getTagId().equals(tag.getId())){
								topic.setTagName(tag.getName());
								break;
							}
						}
						
					}
				}
				
			}

			pageView.setQueryResult(qr);
			
			
			model.addAttribute("pageView", pageView);
		}
		
		
		

		return "jsp/user/allTopicList";
	}
	
	/**
	 * ???????????????
	 * @param pageForm
	 * @param model
	 * @param userName ????????????
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(params="method=allComment",method=RequestMethod.GET)
	public String allAuditComment(PageForm pageForm,ModelMap model,String userName,
			HttpServletRequest request, HttpServletResponse response)
			throws Exception {

		if(userName != null && !"".equals(userName.trim())){
			StringBuffer jpql = new StringBuffer("");
			//???????????????
			List<Object> params = new ArrayList<Object>();

			jpql.append(" and o.userName=?"+ (params.size()+1));
			params.add(userName.trim());
			
			//???????????????and
			String _jpql = org.apache.commons.lang3.StringUtils.difference(" and", jpql.toString());
			
			PageView<Comment> pageView = new PageView<Comment>(settingService.findSystemSetting_cache().getBackstagePageNumber(),pageForm.getPage(),10);
			//?????????
			int firstindex = (pageForm.getPage()-1)*pageView.getMaxresult();;	
			//??????
			LinkedHashMap<String,String> orderby = new LinkedHashMap<String,String>();
			
			orderby.put("id", "desc");//??????id??????????????????
			
			
			//?????????????????????
			QueryResult<Comment> qr = commentService.getScrollData(Comment.class, firstindex, pageView.getMaxresult(), _jpql, params.toArray(),orderby);
			if(qr != null && qr.getResultlist() != null && qr.getResultlist().size() >0){
				List<Long> topicIdList = new ArrayList<Long>();
				for(Comment o :qr.getResultlist()){
	    			o.setContent(textFilterManage.filterText(o.getContent()));
	    			if(!topicIdList.contains(o.getTopicId())){
	    				topicIdList.add(o.getTopicId());
	    			}
	    		}
				List<Topic> topicList = topicService.findTitleByIdList(topicIdList);
				if(topicList != null && topicList.size() >0){
					for(Comment o :qr.getResultlist()){
						for(Topic topic : topicList){
							if(topic.getId().equals(o.getTopicId())){
								o.setTopicTitle(topic.getTitle());
								break;
							}
						}
						
					}
				}
				
			}

			pageView.setQueryResult(qr);
			
			
			model.addAttribute("pageView", pageView);
		}
		

		return "jsp/user/allCommentList";
	}
	
	/**
	 * ???????????????
	 * @param pageForm
	 * @param model
	 * @param userName ????????????
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(params="method=allReply",method=RequestMethod.GET)
	public String allAuditReply(PageForm pageForm,ModelMap model,String userName,
			HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		if(userName != null && !"".equals(userName.trim())){
			StringBuffer jpql = new StringBuffer("");
			//???????????????
			List<Object> params = new ArrayList<Object>();

			jpql.append(" and o.userName=?"+ (params.size()+1));
			params.add(userName.trim());
			
			//???????????????and
			String _jpql = org.apache.commons.lang3.StringUtils.difference(" and", jpql.toString());
			
			PageView<Reply> pageView = new PageView<Reply>(settingService.findSystemSetting_cache().getBackstagePageNumber(),pageForm.getPage(),10);
			//?????????
			int firstindex = (pageForm.getPage()-1)*pageView.getMaxresult();;	
			//??????
			LinkedHashMap<String,String> orderby = new LinkedHashMap<String,String>();
			
			orderby.put("id", "desc");//??????id??????????????????
			
			
			//?????????????????????
			QueryResult<Reply> qr = commentService.getScrollData(Reply.class, firstindex, pageView.getMaxresult(), _jpql, params.toArray(),orderby);
			if(qr != null && qr.getResultlist() != null && qr.getResultlist().size() >0){
				List<Long> topicIdList = new ArrayList<Long>();
				for(Reply o :qr.getResultlist()){
	    				
	    			o.setContent(textFilterManage.filterText(o.getContent()));
	    			if(!topicIdList.contains(o.getTopicId())){
	    				topicIdList.add(o.getTopicId());
	    			}
	    		}
				List<Topic> topicList = topicService.findTitleByIdList(topicIdList);
				if(topicList != null && topicList.size() >0){
					for(Reply o :qr.getResultlist()){
						for(Topic topic : topicList){
							if(topic.getId().equals(o.getTopicId())){
								o.setTopicTitle(topic.getTitle());
								break;
							}
						}
						
					}
				}
				
			}

			pageView.setQueryResult(qr);
			
			
			model.addAttribute("pageView", pageView);
		}
		

		return "jsp/user/allReplyList";
	}
	
	
	/**
	 * ????????????
	 * @param model
	 * @param imgFile
	 * @param id ??????Id
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(params="method=updateAvatar",method=RequestMethod.POST)
	@ResponseBody//????????????ajax,?????????????????????
	public String updateAvatar(ModelMap model,MultipartFile imgFile,Long id,
			HttpServletRequest request,HttpServletResponse response)
			throws Exception {	
		
		Map<String,String> error = new HashMap<String,String>();//??????

	
		User user = userService.findUserById(id);
		
		if(user == null){
			error.put("user", "???????????????");
		}
		
		String _width = request.getParameter("width");
		String _height = request.getParameter("height");
		String _x = request.getParameter("x");
		String _y = request.getParameter("y");
		
		
		Integer width = null;//???
		Integer height = null;//???
		Integer x = 0;//??????X???
		Integer y = 0;//??????Y???
		
		
		if(_width != null && !"".equals(_width.trim())){
			if(Verification.isPositiveInteger(_width.trim())){
				if(_width.trim().length() >=8){
					error.put("width", "????????????8?????????");//????????????8?????????
				}else{
					width = Integer.parseInt(_width.trim());
				}
				
				
			}else{
				error.put("width", "??????????????????0");//??????????????????0
			}
			
		}
		if(_height != null && !"".equals(_height.trim())){
			if(Verification.isPositiveInteger(_height.trim())){
				if(_height.trim().length() >=8){
					error.put("height", "????????????8?????????");//????????????8?????????
				}else{
					height = Integer.parseInt(_height.trim());
				}
				
			}else{
				error.put("height", "??????????????????0 ");//??????????????????0 
			}
		}
		
		if(_x != null && !"".equals(_x.trim())){
			if(Verification.isPositiveIntegerZero(_x.trim())){
				if(_x.trim().length() >=8){
					error.put("x", "????????????8?????????");//????????????8?????????
				}else{
					x = Integer.parseInt(_x.trim());
				}
				
			}else{
				error.put("x", "X????????????????????????0");//X????????????????????????0
			}
			
		}
		
		if(_y != null && !"".equals(_y.trim())){
			if(Verification.isPositiveIntegerZero(_y.trim())){
				if(_y.trim().length() >=8){
					error.put("y","????????????8?????????");//????????????8?????????
				}else{
					y = Integer.parseInt(_y.trim());
				}
				
			}else{
				error.put("y","Y????????????????????????0");//Y????????????????????????0
			}
			
		}
		//??????????????????
		String newFileName = "";
		if(error.size() ==0){
			
			DateTime dateTime = new DateTime(user.getRegistrationDate());     
			String date = dateTime.toString("yyyy-MM-dd");
			
			if(imgFile != null && !imgFile.isEmpty()){
				//??????????????????
				String fileName = imgFile.getOriginalFilename();
				
				//????????????
				Long size = imgFile.getSize();
				

				
				//????????????????????????
				List<String> formatList = new ArrayList<String>();
				formatList.add("gif");
				formatList.add("jpg");
				formatList.add("jpeg");
				formatList.add("bmp");
				formatList.add("png");
				//???????????????????????? ??????KB
				long imageSize = 3*1024L;
				
				if(size/1024 <= imageSize){
					
					//??????????????????
					boolean authentication = fileManage.validateFileSuffix(imgFile.getOriginalFilename(),formatList);
			
					if(authentication){
						//??????????????????;?????????????????????????????????????????????,??????????????????
						String pathDir = "file"+File.separator+"avatar"+File.separator + date +File.separator ;
						//100*100??????
						String pathDir_100 = "file"+File.separator+"avatar"+File.separator + date +File.separator +"100x100" +File.separator;

						//????????????????????????
						fileManage.createFolder(pathDir);
						//????????????????????????
						fileManage.createFolder(pathDir_100);
						
						if(user.getAvatarName() != null && !"".equals(user.getAvatarName().trim())){
							String oldPathFile = pathDir + user.getAvatarName();
							//???????????????
							fileManage.deleteFile(oldPathFile);
							String oldPathFile_100 = pathDir_100 + user.getAvatarName();
							//???????????????100*100
							fileManage.deleteFile(oldPathFile_100);
						}

						BufferedImage bufferImage = ImageIO.read(imgFile.getInputStream());  
			            //????????????????????????  
			            int srcWidth = bufferImage.getWidth();  
			            int srcHeight = bufferImage.getHeight();  
						
						//??????????????????
						String suffix = fileManage.getExtension(fileName).toLowerCase();
						//??????????????????
						newFileName = UUIDUtil.getUUID32()+ "." + suffix;
						
						if(srcWidth <=200 && srcHeight <=200){	
							//????????????
							fileManage.writeFile(pathDir, newFileName,imgFile.getBytes());
							
							if(srcWidth <=100 && srcHeight <=100){
								//????????????
								fileManage.writeFile(pathDir_100, newFileName,imgFile.getBytes());
							}else{
								//??????100*100?????????
								thumbnailManage.createImage(imgFile.getInputStream(),PathUtil.path()+File.separator+pathDir_100+newFileName,suffix,100,100);
							}
						}else{
							//??????200*200?????????
							thumbnailManage.createImage(imgFile.getInputStream(),PathUtil.path()+File.separator+pathDir+newFileName,suffix,x,y,width,height,200,200);

							//??????100*100?????????
							thumbnailManage.createImage(imgFile.getInputStream(),PathUtil.path()+File.separator+pathDir_100+newFileName,suffix,x,y,width,height,100,100);
    
						}	
					}else{
						error.put("imgFile","?????????????????????????????????");//?????????????????????????????????
					}	
				}else{
					error.put("imgFile","??????????????????????????????");//??????????????????????????????
				}	
			}else{
				error.put("imgFile","??????????????????");//??????????????????
			}
		}
		

		if(error.size() ==0){
			userService.updateUserAvatar(user.getUserName(), newFileName);
			//????????????
			userManage.delete_cache_findUserById(user.getId());
			userManage.delete_cache_findUserByUserName(user.getUserName());
		}
		
		
		

		
		Map<String,Object> returnValue = new HashMap<String,Object>();//?????????

		if(error != null && error.size() >0){
			returnValue.put("success", "false");
			returnValue.put("error", error);
		}else{
			returnValue.put("success", "true");
		}
		return JsonUtils.toJSONString(returnValue);
	}
	
}
